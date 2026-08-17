package net.innoventa.identity.mcp;

import lombok.RequiredArgsConstructor;
import org.jmouse.ai.mcp.authorization.AuthorizationDocuments;
import org.jmouse.ai.mcp.authorization.AuthorizationRoutes;
import org.jmouse.ai.mcp.authorization.server.McpAuthorizationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * How a client finds out that any of this exists.
 *
 * <p>Read <strong>before</strong> the reader holds any credential — that is the entire reason it exists
 * — so it is public by necessity, and everything it discloses is a route that was already reachable.
 *
 * <h2>⚠️ Identity is the one product where the obvious address is already taken</h2>
 *
 * <p>Tessera serves its protocol metadata at {@code /.well-known/oauth-authorization-server}, because
 * nothing else on that host is an authorization server. <strong>Identity is one.</strong> Spring
 * Authorization Server already answers that exact path with this service's real metadata — the document
 * describing {@code /oauth2/authorize}, the JWKS, and famously <em>no</em>
 * {@code registration_endpoint}, which is the whole reason a protocol client cannot use it and the whole
 * reason this flow exists.
 *
 * <p>So the two documents cannot share an address, and the standard already says what to do about it:
 * RFC 8414 §3.1 puts the metadata for an issuer that has a path component at
 * {@code /.well-known/oauth-authorization-server/{path}}. The protocol's issuer is therefore
 * {@code {resource}/api/agents/authorization} rather than the bare host, its metadata lives under the
 * matching suffix, and Spring's document at the bare path is left exactly as it is.
 *
 * <p>⚠️ <strong>Two authorization servers on one host is the case the suffix was invented for</strong>,
 * so this is the standard working rather than a trick — but it is also the part most likely to surprise
 * a client, and the reason the protected-resource document below names the issuer explicitly instead of
 * letting anything infer it.
 */
@RestController
@RequiredArgsConstructor
public class McpDiscoveryController {

    private final McpAuthorizationSettings   settings;
    private final McpAuthorizationProperties authorization;

    /**
     * What this endpoint is, and which authorization server speaks for it (RFC 9728).
     *
     * <p>⚠️ Served here rather than by Spring Security's own filter, which grew the ability to publish
     * this document only after the version this Boot line ships. The address in every 401's
     * {@code WWW-Authenticate} header points at exactly this route — see
     * {@code McpAuthorizationConfiguration}, and keep the two in step.
     *
     * <p>⚠️ Header-only bearer methods: a token in a query string ends up in access logs, browser
     * history and proxy caches, and the protocol needs neither of the other two ways.
     */
    @GetMapping({
        AuthorizationRoutes.PROTECTED_RESOURCE_METADATA,
        AuthorizationRoutes.PROTECTED_RESOURCE_METADATA + AuthorizationRoutes.ANY_RESOURCE_SUFFIX
    })
    public Map<String, Object> protectedResource() {
        return Map.of(
            "resource", settings.resourceUrl() + McpEndpoint.PATH,
            "resource_name", "Identity",
            "authorization_servers", java.util.List.of(protocolIssuer()),
            "scopes_supported", java.util.List.of(AuthorizationRoutes.SCOPE),
            "bearer_methods_supported", java.util.List.of("header"),
            "tls_client_certificate_bound_access_tokens", false);
    }

    /**
     * Where a client registers, authorizes, and renews (RFC 8414).
     *
     * <p>⚠️ Mapped at the <strong>path-suffixed</strong> location only. The bare
     * {@code /.well-known/oauth-authorization-server} belongs to this service's real authorization
     * server and mapping a second handler there would either fail at startup or shadow the document
     * every browser client depends on — see the class note.
     */
    @GetMapping(AuthorizationRoutes.AUTHORIZATION_SERVER_METADATA + PROTOCOL_ISSUER_PATH)
    public Map<String, Object> authorizationServer() {
        AuthorizationRoutes routes = authorization.routes();

        return AuthorizationDocuments.authorizationServer(
            protocolIssuer(),
            settings.resourceUrl() + routes.authorization(),
            settings.resourceUrl() + routes.token(),
            settings.resourceUrl() + routes.registration(),
            AuthorizationRoutes.SCOPE);
    }

    /**
     * ⚠️ The literal the mapping needs, because an annotation attribute cannot be built at runtime.
     * It must equal {@code McpAuthorizationProperties.DEFAULT_PROTOCOL_PREFIX}; changing that prefix in
     * configuration without changing this makes the metadata unreachable at the address the issuer
     * implies, which a client reports as "no authorization server" rather than as a mismatch.
     */
    private static final String PROTOCOL_ISSUER_PATH =
        McpAuthorizationProperties.DEFAULT_PROTOCOL_PREFIX;

    /** The issuer the protocol's own authorization server goes by — a path, deliberately. */
    private String protocolIssuer() {
        return settings.resourceUrl() + PROTOCOL_ISSUER_PATH;
    }
}
