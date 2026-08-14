package net.innoventa.identity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6 resolves the CSRF token lazily — the {@code XSRF-TOKEN} cookie is only written
 * once something actually reads {@link CsrfToken#getToken()}. A server-rendered Thymeleaf form did
 * that implicitly via {@code th:action}; a pure JSON SPA never touches the token attribute at all,
 * so without this filter the cookie would simply never appear. Forcing the read here on every
 * request is what makes {@code CookieCsrfTokenRepository.withHttpOnlyFalse()} actually deliver the
 * cookie to the browser.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }

}
