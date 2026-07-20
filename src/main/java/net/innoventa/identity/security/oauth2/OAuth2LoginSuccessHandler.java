package net.innoventa.identity.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.service.OAuth2AccountService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Extraction logic (email required, display name falling back to GitHub's {@code login} attribute)
 * mirrors Innoventa's {@code OAuth2LoginSuccessHandler} exactly. What's necessarily different:
 * Innoventa is a stateless JWT backend that issues a one-time code and redirects to a separate SPA
 * to exchange it; Identity's human-facing chain is session-based (same as its form login), so
 * there's no code/exchange dance — instead, the {@link OAuth2AuthenticationToken} Spring Security
 * puts in the {@link SecurityContext} after a successful Google/GitHub login (principal = whatever
 * attributes that provider returned, name = something provider-specific) gets swapped for a
 * standard session-persisted {@link UsernamePasswordAuthenticationToken} keyed by the resolved
 * {@link IdentityUser}'s own id — the same principal shape {@code IdentityUserDetailsService}
 * produces for form login, so every downstream consumer (the OIDC token issuance, AccountController)
 * sees one consistent kind of principal regardless of how the user actually signed in. Delegates to
 * {@link SavedRequestAwareAuthenticationSuccessHandler} afterward so a login that started from
 * {@code /oauth2/authorize} (Innoventa/Moneta's OIDC flow) still replays back there —
 * identical to what form login already does.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AccountService oAuth2AccountService;
    private final AuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        Provider provider = Provider.valueOf(registrationId.toUpperCase());

        String email = extractEmail(oauthUser, registrationId);
        String displayName = extractDisplayName(oauthUser);

        IdentityUser identityUser = oAuth2AccountService.upsertOAuth2User(email, displayName, provider);

        // IdentityUserDetailsService already rejects a disabled LOCAL user at the password-check
        // step (`.filter(IdentityUser::isEnabled)`) — this path had no equivalent gate at all: an
        // admin disabling a GOOGLE/GITHUB-provider account had zero effect, since that user could
        // just click "Continue with Google" again and get a fresh session. Cleared explicitly
        // (rather than throwing and trusting the filter chain's exception routing to land somewhere
        // sensible) so the reject is unconditional regardless of what Spring's OAuth2 login filter
        // already set in the security context before this handler ran.
        if (!identityUser.isEnabled()) {
            log.warn("OAuth2 login rejected: userId={} provider={} is disabled", identityUser.getId(), provider);
            SecurityContextHolder.clearContext();
            request.getSession().invalidate();
            response.sendRedirect(request.getContextPath() + "/login?error=disabled");
            return;
        }

        log.info("OAuth2 login success: userId={} provider={}", identityUser.getId(), provider);

        Authentication sessionAuthentication = swapToSessionAuthentication(request, identityUser);
        delegate.onAuthenticationSuccess(request, response, sessionAuthentication);
    }

    private Authentication swapToSessionAuthentication(HttpServletRequest request, IdentityUser identityUser) {
        List<GrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority("ROLE_" + identityUser.getRole().name()));
        Authentication sessionAuthentication =
            new UsernamePasswordAuthenticationToken(identityUser.getId(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        SecurityContextHolder.setContext(context);
        request.getSession()
            .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return sessionAuthentication;
    }

    private String extractEmail(OAuth2User oauthUser, String registrationId) {
        String email = oauthUser.getAttribute("email");
        if (email == null) {
            throw new IllegalStateException(
                "OAuth2 provider '" + registrationId + "' did not return an email address");
        }
        return email;
    }

    private String extractDisplayName(OAuth2User oauthUser) {
        String name = oauthUser.getAttribute("name");
        return name != null ? name : oauthUser.getAttribute("login"); // GitHub uses "login"
    }

}
