package net.innoventa.identity.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Spring Security's official recipe for a browser SPA that reads its CSRF token straight from the
 * {@code XSRF-TOKEN} cookie (see {@link CsrfCookieFilter}) instead of having a server-rendered form
 * echo it back. A same-origin form POST still sends the BREACH-protected masked value, so that path
 * still goes through {@link XorCsrfTokenRequestAttributeHandler}; a header carrying the raw cookie
 * value is resolved with the plain {@link CsrfTokenRequestAttributeHandler} instead, since the Xor
 * handler would reject an unmasked value as invalid.
 */
final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(headerValue)
            ? super.resolveCsrfTokenValue(request, csrfToken)
            : delegate.resolveCsrfTokenValue(request, csrfToken);
    }

}
