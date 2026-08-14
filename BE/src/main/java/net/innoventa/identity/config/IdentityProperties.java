package net.innoventa.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(String issuer, Map<String, ClientProperties> clients) {

    /**
     * {@code publicClient} distinguishes a browser SPA (no client secret, PKCE required,
     * {@code clientSecret} ignored) from a confidential server-side client (secret + HTTP Basic,
     * the original and still-default shape). See {@code SecurityConfiguration.buildRegisteredClient}.
     *
     * <p>{@code postLogoutRedirectUri} must be registered for RP-Initiated Logout
     * ({@code /connect/logout}, Spring Authorization Server's default OIDC end-session endpoint —
     * enabled automatically, no extra config needed) to actually redirect back to the app after
     * logging out; without a matching registered URI, the server won't redirect there at all.
     */
    public record ClientProperties(String clientId, String clientSecret, String redirectUri,
                                    String postLogoutRedirectUri, String audience, boolean publicClient) {
    }

}
