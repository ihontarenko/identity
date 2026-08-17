package net.innoventa.identity.mcp.tool;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Carries the signed-in caller across the thread hop a tool call makes.
 *
 * <h2>⚠️ Why this exists, and why it is not in Tessera</h2>
 *
 * <p>A tool handler runs on a different thread than the HTTP request that carried the credential.
 * {@code jmouse-ai-mcp} switches Micrometer's context propagation on for exactly that reason — but
 * propagation only moves what somebody has registered an <em>accessor</em> for, and the accessor for
 * {@link SecurityContextHolder} is Spring Security's own.
 *
 * <p><strong>Spring Security ships it from 6.4; the version this Boot line pins is 6.3.1, which does
 * not.</strong> So the propagation reported itself enabled, knew one accessor — somebody else's — and
 * every tool call refused as {@code NO_CALLER} while the credential was perfectly valid. The endpoint
 * initialised, listed its tools, and refused to run any of them.
 *
 * <p>That is the real thing the MCP stack wants a newer Boot for, and it is worth naming precisely
 * because the <em>documented</em> reason was Jackson — which turned out to be a `jackson-bom` import
 * (see the pom). One of the two blockers dissolved; this one is genuine and this class is the backport.
 *
 * <p>⚠️ <strong>Delete it when Identity moves to Boot 4.</strong> At Spring Security 6.4+ this is a
 * duplicate registration of a class the framework provides, and a second accessor for one thread-local
 * is a coin toss about which one wins.
 *
 * <h2>⚠️ Null for an empty context, deliberately</h2>
 *
 * <p>{@link SecurityContextHolder#getContext()} never returns null — it creates an empty context on
 * demand. Capturing that would mean every snapshot carries "signed in as nobody", and restoring it on
 * a thread that had a caller would take the caller away. Returning null keeps the snapshot honest:
 * there was nothing to carry. Spring Security's own implementation does the same.
 */
@Component
public class SecurityContextPropagation implements ThreadLocalAccessor<SecurityContext> {

    /** The same key Spring Security uses, so the two cannot both be registered under different names. */
    public static final String KEY = "SECURITY_CONTEXT";

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextPropagation.class);

    @PostConstruct
    void register() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(this);

        LOGGER.info("🧵 The security context now travels with a tool call. Registered by hand because "
                    + "Spring Security ships its own accessor from 6.4 and this build is older — "
                    + "delete this class when Identity moves to Boot 4.");
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public SecurityContext getValue() {
        SecurityContext context = SecurityContextHolder.getContext();

        return context.getAuthentication() == null ? null : context;
    }

    @Override
    public void setValue(SecurityContext value) {
        SecurityContextHolder.setContext(value);
    }

    @Override
    public void setValue() {
        SecurityContextHolder.clearContext();
    }
}
