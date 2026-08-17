package net.innoventa.identity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * While an account is holding a password somebody else chose for it, the session reaches the
 * change-password call and nothing else.
 *
 * <h2>⚠️ Why this is a filter and not a hidden button</h2>
 *
 * <p>An administrator raising an account types the first password, so for a moment two people know it.
 * The flag makes that moment end. Enforced anywhere softer — a screen the interface declines to show,
 * a route the router redirects away from — the person still holds a working credential at
 * {@code /oauth2/authorize}, and <strong>walks straight into Innoventa, Moneta, Tessera and WiQ with a
 * password their administrator chose</strong>. The flag would be decoration and the feature a
 * liability.
 *
 * <p>So it is registered on <strong>both</strong> security filter chains: the authorization-server one,
 * where it stops the token flow, and the default one, where it stops the API.
 *
 * <h2>What still gets through, and why each one has to</h2>
 *
 * <ul>
 *   <li>{@code GET /api/me} — the interface cannot render the change-password screen without knowing
 *       that it should.
 *   <li>{@code PUT /api/account/password} — the way out. Blocking it would be a lockout with no
 *       recovery short of a database edit.
 *   <li>{@code POST /api/authentication/login} and {@code /logout} — signing in has to work to reach
 *       this state at all, and signing out has to work to leave it.
 *   <li>Anything that is not an API call or an authorization-server endpoint — the single-page shell,
 *       its assets, the health probe. They carry no authority; blocking them would serve a blank page
 *       to somebody who needs to read an explanation.
 * </ul>
 *
 * <h2>⚠️ The cost, stated rather than hidden</h2>
 *
 * <p>It reads the account row on every authenticated request that is not on the list above. The cheap
 * alternative — stamping the flag into the session at sign-in — is <strong>wrong in the dangerous
 * direction</strong>: there is more than one way a session is created here (a JSON login, a Google
 * callback, a GitHub callback), and a path that forgot to stamp it produces a session the gate never
 * applies to, silently. A primary-key lookup is a small price beside a password check, and this is an
 * authorization server rather than a hot path.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    /** The machine-readable marker the interface routes on, beside the prose. */
    public static final String REASON = "password-change-required";

    private static final List<String> ALWAYS_ALLOWED = List.of(
        "/api/me",
        "/api/account/password",
        "/api/authentication/login",
        "/api/authentication/logout");

    private final IdentityUserRepository identityUserRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        if (!isGoverned(request) || !mustChangePassword()) {
            chain.doFilter(request, response);
            return;
        }

        refuse(request, response);
    }

    /**
     * Whether this request is one the flag has anything to say about.
     *
     * <p>⚠️ Everything outside {@code /api} and the OAuth2 endpoints passes: those are the shell and
     * its assets, and a person who has to change their password still needs the page that tells them
     * so.
     */
    private boolean isGoverned(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (ALWAYS_ALLOWED.contains(path)) {
            return false;
        }

        return path.startsWith("/api/") || path.startsWith("/oauth2/") || path.startsWith("/connect/");
    }

    private boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return identityUserRepository.findById(authentication.getName())
            .map(account -> account.isMustChangePassword())
            .orElse(false);
    }

    /**
     * ⚠️ <strong>403 and never a redirect, even for a browser navigation.</strong> The one navigation
     * that reaches here is {@code /oauth2/authorize}, and redirecting it back to this service would
     * hand the waiting application a response it cannot read — it is waiting for an authorization code.
     * Refusing outright leaves the person on a page that says what to do.
     */
    private void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
            {"type":"about:blank","title":"Change your password first","status":403,\
            "detail":"This account is using a password an administrator set for it. Change it before \
            doing anything else, here or in any application that signs in through Identity.",\
            "instance":"%s","reason":"%s"}""".formatted(request.getRequestURI(), REASON));
    }
}
