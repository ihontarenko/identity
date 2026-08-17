package net.innoventa.identity.config;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import net.innoventa.identity.mcp.McpEndpoint;
import org.jmouse.ai.ToolCatalog;
import org.jmouse.ai.ToolDispatcher;
import org.jmouse.ai.mcp.McpToolServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The Model Context Protocol, served — and, for now, serving nothing.
 *
 * <h2>⚠️ An empty catalogue on purpose</h2>
 *
 * <p>This ticket is the transport and the credential; the tools are ID-10. A server with no tools is a
 * perfectly good MCP server — it handshakes, answers {@code tools/list} with an empty list, and lets a
 * client be added and its credential proven before anything can be called with it. Building the two
 * together would have meant discovering an authorization bug through a tool failure.
 *
 * <p>⚠️ <strong>{@code ToolDispatcher.over} takes every seam at its default, and one of those defaults
 * is not permissive</strong> — the scope resolver refuses. That is the correct posture for a catalogue
 * that is empty and the wrong one the moment a tool exists, so ID-10 replaces this dispatcher rather
 * than adding to it.
 *
 * <h2>⚠️ A servlet, not a Spring MCP module</h2>
 *
 * <p>{@link HttpServletStreamableServerTransportProvider} is a plain {@code HttpServlet} already on the
 * classpath, so it is registered by hand — which also puts it behind the ordinary servlet filter chain
 * and therefore behind Spring Security, where {@code McpAuthorizationConfiguration}'s chain
 * authenticates it.
 *
 * <h2>⚠️ Why this did NOT force Identity onto Boot 4</h2>
 *
 * <p>The SDK ships one JSON binding, Jackson 3, and Jackson 3 kept its annotations in Jackson 2's
 * package — so on a Boot line that pins {@code jackson-annotations} at 2.17.2 the server refuses to
 * build at all, with {@code NoSuchFieldError: … JsonFormat$Shape does not have member field 'POJO'}.
 * Tessera met this and concluded the SDK forced the move to Boot 4. It does not: importing
 * {@code jackson-bom:2.21.4} — the set Boot 4.0.7 resolves — reproduces exactly that classpath while
 * everything else here stays on 3.3.2. See the pom.
 */
@Configuration
public class McpConfiguration {

    /**
     * What a client is told this server is, and what it is for.
     *
     * <p>The instructions are read by the model before it calls anything, so they carry the one rule
     * that is otherwise learned by being refused.
     */
    @Bean
    public McpToolServer mcpToolServer(@Value("${identity.version:0.1.0}") String version) {
        return new McpToolServer(
            ToolDispatcher.over(ToolCatalog.of(List.of())), "identity", version,
            "Identity is the sign-in service the other applications trust. It holds accounts and what "
            + "each account may do INSIDE Identity itself — raising an account, blocking one, handing "
            + "out a role — and nothing about what anybody may do in any other product, which each of "
            + "those answers for itself. This connection acts as the person who approved it and holds "
            + "exactly what they hold: an action refused here would have been refused on the screen too.");
    }

    /**
     * The transport, as a servlet.
     *
     * <p>⚠️ <strong>The mapper is resolved here rather than left to the transport's own default.</strong>
     * {@link McpJsonDefaults#getMapper()} is a {@code ServiceLoader} lookup, and a lookup that finds
     * nothing fails at the first call rather than at startup — a protocol that accepts a connection and
     * then cannot answer it. Asking for it while building the bean moves that failure to the boot.
     */
    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(McpJsonDefaults.getMapper())
            .mcpEndpoint(McpEndpoint.PATH)
            .build();
    }

    /**
     * ⚠️ A bean rather than a local: {@code serving} starts the session factory, and a server nothing
     * holds is a server whose lifecycle nothing closes.
     */
    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpSyncServer(
        McpToolServer tools, HttpServletStreamableServerTransportProvider transport) {

        return tools.serving(transport);
    }

    /**
     * ⚠️ <strong>Registered under the same constant the security rule uses.</strong> A servlet mapped
     * somewhere the filter chain does not cover is the protocol served outside its own security
     * boundary — reachable, unauthenticated, and wrong from neither side alone.
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
        HttpServletStreamableServerTransportProvider transport) {

        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
            new ServletRegistrationBean<>(transport, McpEndpoint.ALL_PATTERN);

        registration.setName("mcp");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);

        return registration;
    }
}
