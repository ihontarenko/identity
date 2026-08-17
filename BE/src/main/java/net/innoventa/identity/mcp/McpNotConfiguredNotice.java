package net.innoventa.identity.mcp;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Says out loud that the Model Context Protocol is switched off, and how to switch it on.
 *
 * <p>Without this the feature's absence is silent: {@code /api/mcp} answers 404, the discovery
 * documents are not served, and nothing anywhere explains that this is a configuration decision rather
 * than a broken build. One line at startup is the difference between "not configured" and "why is this
 * not working".
 *
 * <p>⚠️ <strong>The condition means "the property is absent", spelled the only way Spring offers.</strong>
 * {@code havingValue = ""} matches a property explicitly set to empty and {@code matchIfMissing} covers
 * one that is not there at all; a real secret matches neither, so this bean disappears exactly when
 * {@link OnMcpConfigured} appears. The two are opposites and must stay so — if a future edit makes both
 * true at once, the log will contradict the routes.
 */
@Component
@ConditionalOnProperty(prefix = "identity.mcp", name = "signing-secret",
                       havingValue = "", matchIfMissing = true)
public class McpNotConfiguredNotice {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpNotConfiguredNotice.class);

    @PostConstruct
    void say() {
        LOGGER.info("🔌 The Model Context Protocol is off: identity.mcp.signing-secret is not set, so "
                    + "/api/mcp is not served and no protocol credential can be issued. Nothing else is "
                    + "affected. Set IDENTITY_MCP_SIGNING_SECRET to at least 32 bytes to turn it on — "
                    + "and keep the same value across restarts, because it is what every issued "
                    + "credential was signed with.");
    }
}
