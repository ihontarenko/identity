package net.innoventa.identity.config;

import net.innoventa.identity.mcp.OnMcpConfigured;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import net.innoventa.identity.mcp.McpAuthorizationSettings;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import net.innoventa.identity.mcp.McpCredentialService;
import net.innoventa.identity.mcp.McpCredentialValidator;
import net.innoventa.identity.mcp.McpEndpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;

/**
 * The protocol credential: what signs it, what verifies it, and what accepts it.
 *
 * <h2>⚠️ An HS256 key inside the service that owns an RSA one, and that is the whole design</h2>
 *
 * <p>Identity mints RS256 tokens every product verifies through JWKS. A protocol credential must be the
 * opposite: unusable anywhere but the endpoint it was issued for. So it is signed with a secret nobody
 * else has, and presented to Innoventa or Tessera it is not a token that fails a check — it is a
 * signature that does not verify. See {@code docs/adr/0001-identity-issues-its-own-protocol-credential.md}.
 */
@OnMcpConfigured
@Configuration
public class McpAuthorizationConfiguration {

    /**
     * ⚠️ Ordered ahead of both existing chains. The protocol endpoint is bearer-authenticated and
     * stateless; falling through to the session chain would redirect a program to a login page.
     */
    public static final int MCP_CHAIN_ORDER = 0;

    @Bean
    public McpAuthorizationSettings mcpAuthorizationSettings(
        @Value("${identity.mcp.resource-url:${identity.issuer:http://localhost:9090}}") String resourceUrl,
        @Value("${identity.mcp.audience:identity-mcp}") String audience,
        @Value("${identity.mcp.signing-secret:}") String signingSecret,
        @Value("${identity.mcp.access-token-lifetime:1h}") Duration accessTokenLifetime,
        @Value("${identity.mcp.refresh-token-lifetime:30d}") Duration refreshTokenLifetime) {

        return new McpAuthorizationSettings(
            resourceUrl, audience, requireUsableSecret(signingSecret),
            accessTokenLifetime, refreshTokenLifetime);
    }

    /**
     * ⚠️ <strong>A secret that is too SHORT stops the boot. A secret that is ABSENT does not — it
     * switches the feature off.</strong>
     *
     * <p>The distinction is the whole of what {@link OnMcpConfigured} exists for, and the first version
     * did not make it: with no secret, this method threw and <strong>the entire application refused to
     * start</strong>, for everybody, including the majority of runs that want nothing to do with the
     * protocol. A guard that breaks the common case to protect the rare one is not a guard.
     *
     * <p>So absence is a choice and never reaches here — this class does not exist without the property.
     * Sixteen bytes is a mistake, and a mistake still stops the boot, because a secret somebody set
     * badly is worse than one they never set: it looks configured.
     *
     * <p>Never generated per run, in either case. It signs every protocol credential, and one invented
     * at startup would silently break every connection on each restart of the service every other
     * product's sign-in depends on.
     */
    private static String requireUsableSecret(String signingSecret) {
        int length = signingSecret == null
            ? 0
            : signingSecret.getBytes(StandardCharsets.UTF_8).length;

        if (length >= McpAuthorizationSettings.MINIMUM_SECRET_BYTES) {
            return signingSecret;
        }

        throw new IllegalStateException(
            "identity.mcp.signing-secret is " + length + " bytes and HS256 needs at least "
            + McpAuthorizationSettings.MINIMUM_SECRET_BYTES + ". It signs every Model Context Protocol "
            + "credential this service issues, and it is what confines them to the protocol endpoint — "
            + "so it is a property somebody sets rather than one this service invents per run. Set "
            + "IDENTITY_MCP_SIGNING_SECRET.");
    }

    @Bean
    public JwtEncoder mcpJwtEncoder(McpAuthorizationSettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(settings)));
    }

    /**
     * ⚠️ <strong>Its own decoder, and not the one the authorization server publishes.</strong> The two
     * verify different signatures on purpose; sharing a decoder is the single change that would make a
     * protocol credential indistinguishable from an ordinary access token.
     *
     * <p>Issuer and audience are validated as well as the signature, so a secret that leaked still
     * would not mint something the rest of this service honours.
     */
    @Bean
    public JwtDecoder mcpJwtDecoder(
        McpAuthorizationSettings settings, ObjectProvider<McpCredentialService> credentials) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(secretKey(settings))
            .macAlgorithm(HS256)
            .build();

        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validators =
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.resourceUrl()),
                new org.springframework.security.oauth2.jwt.JwtClaimValidator<java.util.List<String>>(
                    "aud", audiences -> audiences != null && audiences.contains(settings.audience())),
                // ⚠️ Resolved lazily: the validator asks the database on every call, and asking for the
                // service here directly would tie a decoder to a transaction manager at bean-creation
                // time.
                new McpCredentialValidator(credentials.getObject()));

        decoder.setJwtValidator(validators);

        return decoder;
    }

    /**
     * What accepts a protocol credential, and nothing else does.
     *
     * <p>⚠️ <strong>Stateless.</strong> A program presenting a bearer token must not acquire a session
     * — a session cookie handed back here would be an ordinary browser credential minted from a
     * confined one, which is the confinement undone in one hop.
     */
    @Bean
    @Order(MCP_CHAIN_ORDER)
    SecurityFilterChain mcpSecurityFilterChain(
        HttpSecurity httpSecurity,
        // ⚠️ QUALIFIED, AND NOT AS A FORMALITY. There are two JwtDecoder beans here — this one and the
        // authorization server's — and a primary candidate beats a parameter-name match, so the obvious
        // spelling silently hands this chain the RSA decoder. The endpoint would then accept exactly
        // the token it exists to refuse and refuse the credential it exists to accept, with nothing
        // anywhere saying so. Tessera found this by presenting both tokens and reading both answers.
        @Qualifier("mcpJwtDecoder") JwtDecoder mcpJwtDecoder,
        McpAuthorizationSettings settings) throws Exception {

        httpSecurity
            // ⚠️ A REQUEST MATCHER, NEVER A PATH PATTERN. `securityMatcher("/api/mcp", "/api/mcp/*")` —
            // the obvious spelling — matches NOTHING: the protocol is served by a servlet of its own
            // mapped at /api/mcp/*, so Spring Security matches patterns against the path WITHIN that
            // servlet, which for POST /api/mcp is empty. The chain never answers, every request falls
            // through to the session chain, and the symptom is the opposite of a security error.
            .securityMatcher(McpAuthorizationConfiguration::isProtocolRequest)
            // A program with a bearer token has no cookie to defend and no form to submit.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.decoder(mcpJwtDecoder))
                // ⚠️ Load-bearing above all else on this chain: the 401 it produces is the FIRST thing
                // a client ever gets, and the `resource_metadata` parameter is the only reason the
                // client knows where discovery starts.
                //
                // ⚠️ Written by hand because Spring Security's own
                // `.protectedResourceMetadata(...)` arrived after the version this Boot line ships.
                // Tessera declares it in one call; here it is an entry point, and the two must keep
                // saying the same thing.
                .authenticationEntryPoint(protocolEntryPoint(settings)));

        return httpSecurity.build();
    }

    /** Whether this request is for the protocol endpoint, asked of the URI itself — see the chain. */
    private static boolean isProtocolRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return McpEndpoint.PATH.equals(uri) || uri.startsWith(McpEndpoint.PATH + "/");
    }

    private static AuthenticationEntryPoint protocolEntryPoint(McpAuthorizationSettings settings) {
        String metadata = settings.resourceUrl() + AuthorizationRoutes.PROTECTED_RESOURCE_METADATA;

        return (request, response, refusal) -> {
            response.setHeader("WWW-Authenticate",
                "Bearer resource_metadata=\"" + metadata + "\"");
            // ⚠️ setStatus, NEVER sendError. `sendError` forwards to /error, that forward is itself a
            // request the session chain wants authenticated, and the client receives a 302 to /login
            // with the header attached to a response it will never read as a challenge. This service
            // has the same trap written up twice already in SecurityConfiguration; it is not specific
            // to this endpoint, it is specific to Identity having a login page at all.
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        };
    }

    private static SecretKeySpec secretKey(McpAuthorizationSettings settings) {
        return new SecretKeySpec(
            settings.signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
