package net.innoventa.identity.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Present only where somebody has configured the protocol's signing secret.
 *
 * <h2>⚠️ Why the whole feature is conditional rather than the boot being refused</h2>
 *
 * <p>{@code identity.mcp.signing-secret} has no default on purpose — it signs every protocol
 * credential and it is what confines them, so a value invented per run would silently invalidate every
 * connection on each restart of the service every other product's sign-in depends on.
 *
 * <p>The first version drew the wrong conclusion from that and <strong>refused to start</strong>
 * without it. Which meant a developer who wants nothing to do with the Model Context Protocol — the
 * overwhelming majority of runs — got a dead application and a stack trace, from a feature they were
 * not using. That is a worse failure than the one it was guarding against, and it is the one that
 * happens every day.
 *
 * <p>So the absence of the secret switches the feature <strong>off</strong> instead. Nothing is
 * weakened: there is no generated key and no fallback credential, the endpoint is simply not served
 * and its routes are not mapped. It is the same idiom the policy library already uses — <em>no
 * locations configured means the document is never read</em> — and the same one this product's own
 * configuration comments describe.
 *
 * <p>⚠️ <strong>A secret that is present but too short still fails the boot</strong>, in
 * {@code McpAuthorizationConfiguration}. Absence is a choice; sixteen bytes is a mistake, and the two
 * deserve different answers.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "identity.mcp", name = "signing-secret")
public @interface OnMcpConfigured {
}
