package net.innoventa.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(String issuer, boolean pinIssuer, Map<String, ClientProperties> clients) {

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
    /**
     * ⚠️ <strong>{@code redirectUris} is a LIST, because OAuth matches a {@code redirect_uri} EXACTLY
     * and offers no wildcard.</strong> One interface is reached at more than one address the moment
     * anybody opens it from another device — {@code localhost:5050} and {@code 192.168.0.104:5050} are
     * two registrations of the same client, and an unregistered one is refused before the person ever
     * sees a login form. The client derives its own {@code redirect_uri} from the address it was
     * opened at; this is the list of addresses it is allowed to have been opened at.
     *
     * <p>⚠️ A DHCP address in here goes stale on its own. Prefer the machine's name over its number
     * when the network hands one out.
     *
     * <p>Spring binds a YAML sequence and a single comma-separated value equally, so one environment
     * variable still configures several.
     *
     * <p>⚠️ <strong>NO COMPACT CONSTRUCTOR ON THIS RECORD, deliberately.</strong> javac drops the
     * generic signature of a record's components when one is declared, and a binder reading
     * {@code List<String>} reflectively then sees a bare {@code List} and cannot resolve the element
     * type. Empty-vs-null is handled where these are read instead — see
     * {@code SecurityConfiguration.buildRegisteredClient}.
     */
    public record ClientProperties(String clientId, String clientSecret, List<String> redirectUris,
                                    List<String> postLogoutRedirectUris, String audience,
                                    boolean publicClient) {
    }

}
