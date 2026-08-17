package net.innoventa.identity.mcp;

import lombok.RequiredArgsConstructor;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.jmouse.ai.mcp.authorization.server.CredentialIssuer;
import org.springframework.stereotype.Component;

/**
 * What minting means in Identity, said to a library that must not know.
 *
 * <p>The shared flow walks the protocol and stops at exactly this line. What it hands over is an opaque
 * reference and a client's self-declared name; what comes back is a token, a refresh token and a
 * lifetime. It never learns that the reference is an account's row id, that the token is HS256, or that
 * the secret behind it is one only Identity holds.
 *
 * <p>⚠️ <strong>Its presence is also the switch.</strong> The shared endpoints are auto-configured only
 * where a {@link CredentialIssuer} bean exists, so deleting this class does not leave three public
 * routes mapped with nothing behind them — it unmaps them.
 *
 * <h2>⚠️ The reference is the person, and there is nothing to choose</h2>
 *
 * <p>Tessera puts an <em>agent account</em> there — a sub-account with permissions of its own, where
 * picking between them is the most important thing on the consent screen. Identity has no such thing: a
 * credential acts as the person who approved it, full stop. That is the simpler of the two shapes the
 * library's {@code ApprovingSubject} was written to allow, and it is why {@link IdentityApprovingSubject}
 * offers exactly one choice.
 *
 * <p>It follows that <strong>a protocol client here is bounded by the same grants the browser is</strong>
 * — see the epic's invariant. There is no persona to widen, and nothing to switch off separately.
 */
@Component
@RequiredArgsConstructor
public class IdentityCredentialIssuer implements CredentialIssuer {

    private final McpCredentialService credentials;

    /**
     * ⚠️ The subject reference is looked at here and nowhere earlier: an approval and its redemption are
     * two requests, and the only party whose standing matters is the one that actually turned up.
     */
    @Override
    public IssuedCredential issue(ApprovedAuthorization approval) {
        return asIssued(credentials.issueFor(
            approval.subjectReference(), approval.clientName(), approval.clientId()));
    }

    /**
     * ⚠️ The refusal has to be the library's exception, not ours. The module answers it with the RFC's
     * {@code invalid_grant}, which is the spelling a client branches on to decide whether to authorize
     * again — anything else and a client with a dead connection retries forever instead of asking.
     */
    @Override
    public IssuedCredential renew(String refreshToken) {
        try {
            return asIssued(credentials.renew(refreshToken));
        } catch (IllegalArgumentException refused) {
            throw new McpAuthorizationException(refused.getMessage());
        }
    }

    private IssuedCredential asIssued(McpCredentialService.IssuedCredential credential) {
        return new IssuedCredential(
            credential.accessToken(), credential.refreshToken(), credential.expiresInSeconds());
    }
}
