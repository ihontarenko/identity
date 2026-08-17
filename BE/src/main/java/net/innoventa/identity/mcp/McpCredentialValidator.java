package net.innoventa.identity.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What makes a self-contained credential revocable: every protocol call asks whether the connection it
 * was issued against is still good for it.
 *
 * <p>Without this, ending a connection would mean waiting out the access token — and a credential that
 * cannot be taken back before it expires is one nobody can safely hand out for a month.
 *
 * <p><strong>The {@code cid} claim is required, and a token without one was not minted here.</strong>
 * Refusing on its absence rather than treating it as "no connection to check" is the difference between
 * a credential this endpoint issued and any other token that happens to verify.
 *
 * <p>⚠️ <strong>It also stamps "last used", which is a write from a validator and deliberate.</strong>
 * This is the one place every protocol call demonstrably passes through; a filter of its own beside the
 * transport would be a second thing to keep in step with the first. The write is rate-limited inside
 * {@link McpCredentialService} so a busy client does not turn every call into an update.
 */
@RequiredArgsConstructor
public class McpCredentialValidator implements OAuth2TokenValidator<Jwt> {

    private final McpCredentialService credentials;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String connectionId = token.getClaimAsString(McpCredentialService.CONNECTION_CLAIM);

        if (connectionId == null || connectionId.isBlank()) {
            return refuse("This token does not name a connection, so it was not issued for the Model "
                          + "Context Protocol endpoint.");
        }

        return credentials.admit(connectionId)
            .map(McpCredentialValidator::refuse)
            .orElseGet(() -> {
                credentials.noteUsage(connectionId);

                return OAuth2TokenValidatorResult.success();
            });
    }

    private static OAuth2TokenValidatorResult refuse(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
