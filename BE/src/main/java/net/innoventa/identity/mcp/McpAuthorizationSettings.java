package net.innoventa.identity.mcp;

import java.time.Duration;

/**
 * What Identity decides about a protocol credential, once the shared flow has taken everything it owns.
 *
 * <p>The walk itself — the routes, the code's lifetime, where a browser is sent to approve — lives in
 * {@code jmouse-ai-mcp-authorization} under {@code jmouse.mcp.authorization}. What is left here is
 * minting, the one thing that library refuses to have an opinion about: what a token claims to be, who
 * honours it, and how long anything lasts.
 *
 * <p>⚠️ <strong>{@code resourceUrl} is the token's {@code iss} as well as the API's address</strong>,
 * and the decoder validates against it. Changing one without the other refuses every credential already
 * issued — at the next call rather than at startup.
 *
 * @param resourceUrl          absolute base address of this API, without a trailing slash
 * @param audience             the {@code aud} claim a protocol token carries. ⚠️ Deliberately not one
 *                             of the audiences under {@code identity.clients}: a protocol credential
 *                             must not read as a token for any product
 * @param signingSecret        the HS256 secret. ⚠️ Only Identity holds it, which is what makes the
 *                             credential unusable anywhere else — a signature that does not verify
 *                             rather than a check somebody has to remember to write
 * @param accessTokenLifetime  how long a minted access token is honoured
 * @param refreshTokenLifetime how long a connection may be renewed for without asking again
 */
public record McpAuthorizationSettings(
    String   resourceUrl,
    String   audience,
    String   signingSecret,
    Duration accessTokenLifetime,
    Duration refreshTokenLifetime
) {

    /** ⚠️ HS256 needs at least this much key, and a short one is a boot failure rather than a weak token. */
    public static final int MINIMUM_SECRET_BYTES = 32;

    public McpAuthorizationSettings {
        resourceUrl = withoutTrailingSlash(resourceUrl);
    }

    private static String withoutTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }

        return url;
    }
}
