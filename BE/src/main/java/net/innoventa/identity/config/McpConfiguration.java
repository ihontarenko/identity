package net.innoventa.identity.config;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import net.innoventa.identity.mcp.McpEndpoint;
import net.innoventa.identity.security.access.Permissions;
import org.jmouse.ai.ToolCatalog;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolDispatcher;
import org.jmouse.ai.guard.GuardChain;
import org.jmouse.ai.mcp.McpToolServer;
import org.jmouse.ai.spi.CallerResolver;
import org.jmouse.ai.spi.InvocationTrace;
import org.jmouse.ai.spi.PermissionVocabulary;
import org.jmouse.ai.spi.ScopeResolver;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * The Model Context Protocol, served — and, for now, serving nothing.
 *
 * <h2>⚠️ Everything here is wiring, and deliberately so</h2>
 *
 * <p>The catalogue is the tool definitions', the decisions are the dispatcher's, and the protocol is
 * the SDK's; this file only says which bean holds which piece. A tool reachable from a client asks
 * {@link net.innoventa.identity.mcp.tool.IdentityToolAuthorizer} for the same permission the equivalent
 * screen control asks the same engine for, against the same person — so <strong>there is no second
 * path into an action and no second place a permission is checked</strong>, and that is a fact the
 * types hold rather than a promise this class keeps.
 *
 * <p>⚠️ <strong>The dispatcher is built with real seams rather than {@code ToolDispatcher.over}.</strong>
 * That convenience takes every seam at its default and one of those defaults is <em>not</em> permissive
 * — it refuses — which was right while the catalogue was empty (ID-9) and would silently refuse
 * everything now.
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
    /**
     * The catalogue, vetted.
     *
     * <p>⚠️ {@link PermissionVocabulary} is what makes a tool declaring a permission nothing grants a
     * <em>boot</em> failure rather than a call that refuses forever. The vocabulary is
     * {@link Permissions#all()} — the same list the policy document is checked against in both
     * directions — so a permission can be misspelt in exactly one place and it is caught in all three.
     */
    @Bean
    public ToolCatalog mcpToolCatalog(List<ToolDefinition> definitions) {
        return ToolCatalog.of(definitions, PermissionVocabulary.of(Set.copyOf(Permissions.all())));
    }

    /**
     * ⚠️ <strong>Every seam named, and {@code ScopeResolver.refusing()} kept on purpose.</strong>
     *
     * <p>Identity has no route and no tool that names a place: both floors are {@code GLOBAL} and
     * {@code SELF}, and no action is {@code scopeConfined()}. So nothing ever asks the scope resolver,
     * and the honest value is the one that would refuse if something did — a permissive resolver would
     * be a hole waiting for the first tool that forgets.
     */
    @Bean
    public ToolDispatcher mcpToolDispatcher(
        ToolCatalog catalog, CallerResolver callerResolver, ToolAuthorizer authorizer) {

        return new ToolDispatcher(
            catalog, callerResolver, authorizer,
            ScopeResolver.refusing(), GuardChain.defaults(), InvocationTrace.none());
    }

    @Bean
    public McpToolServer mcpToolServer(
        ToolDispatcher dispatcher, @Value("${identity.version:0.1.0}") String version) {

        return new McpToolServer(
            dispatcher, "identity", version,
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
