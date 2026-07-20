package net.innoventa.identity.web.rest.dto;

/**
 * {@code redirectTo} is the URL {@code HttpSessionRequestCache} saved before the browser was
 * bounced to {@code /login} — typically {@code /oauth2/authorize?...} when the login was triggered
 * by Innoventa/Moneta's OIDC flow. {@code null} when the visitor just navigated to
 * {@code /login} directly, in which case the SPA falls back to its own landing route.
 */
public record LoginResponse(String redirectTo) {
}
