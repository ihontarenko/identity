package net.innoventa.identity.web.rest;

import net.innoventa.identity.mcp.OnMcpConfigured;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.McpCredential;
import net.innoventa.identity.mcp.McpCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The clients a person has connected to the protocol endpoint, and how they end one.
 *
 * <h2>⚠️ Self-service, and deliberately not administrative</h2>
 *
 * <p>These sit under {@code /api/account} rather than {@code /api/admin} and carry no
 * {@code @RequiresAccess} at all, for the same reason the rest of that prefix does not: a connection is
 * somebody's own key, granted from their own session, acting as nobody but them. It is not an object an
 * administrator curates, and {@code authentication.getName()} <em>is</em> the row id — so these methods
 * cannot reach anybody else's list whatever anybody is granted.
 *
 * <p>⚠️ <strong>Ending a connection takes effect on the next protocol call</strong>, not when the token
 * expires. That is what {@code mcp_credentials} exists for: the credential is self-contained and would
 * otherwise be good for its full hour no matter what this screen said.
 */
@OnMcpConfigured
@RestController
@RequestMapping("/api/account/connections")
@RequiredArgsConstructor
public class McpConnectionController {

    private final McpCredentialService credentials;

    @GetMapping
    public List<ConnectionView> list(Authentication authentication) {
        return credentials.connectionsOf(authentication.getName()).stream()
            .map(ConnectionView::from)
            .toList();
    }

    /**
     * ⚠️ Idempotent by design: ending a connection that is already ended is the same answer as ending
     * one that was live. A person pressing twice, or two tabs disagreeing about the list, is not an
     * error worth a message.
     */
    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable String connectionId) {
        credentials.revoke(authentication.getName(), connectionId);
    }

    /**
     * @param clientName ⚠️ what the client called itself — a claim, shown so a person recognises which
     *                   one this is, never an identity anything is decided on
     */
    public record ConnectionView(
        String        id,
        String        clientName,
        LocalDateTime connectedAt,
        LocalDateTime lastUsedAt,
        boolean       revoked
    ) {

        static ConnectionView from(McpCredential credential) {
            return new ConnectionView(
                credential.getId(),
                credential.getClientName(),
                credential.getCreatedAt(),
                credential.getLastUsedAt(),
                credential.isRevoked());
        }
    }
}
