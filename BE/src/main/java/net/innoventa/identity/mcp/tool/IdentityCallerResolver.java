package net.innoventa.identity.mcp.tool;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.mcp.McpCredentialService;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.jmouse.ai.CallerAttributes;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolRefusedException;
import org.jmouse.ai.spi.CallerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Who Identity is running a tool call as — and the answer is always a person.
 *
 * <p><strong>Simpler than Tessera's, and the simplicity is the epic's invariant.</strong> There a
 * credential names an <em>agent account</em> with permissions of its own, so the resolver has to decide
 * whether the call authorizes as the agent or as its owner. Identity has no such object: a protocol
 * credential acts as the person who approved it, so caller and acting subject are the same row and
 * <strong>an agent driving this endpoint is bounded by exactly what that person holds</strong> — by
 * construction, not by a check anybody has to remember.
 *
 * <h2>⚠️ There is no request here, and that is the trap this class sits on</h2>
 *
 * <p>A tool call runs on a different thread than the HTTP request that carried the credential. Without
 * something moving the {@code SecurityContext} across that hop, <strong>every call refuses as
 * NO_CALLER while the credential is perfectly valid</strong> — and the natural reaction is to debug the
 * token, which is correct all along. It bit Innoventa and Tessera identically.
 *
 * <p>Nothing in this product does that, on purpose: {@code McpToolServer.serving} switches the
 * propagation on inside {@code jmouse-ai-mcp}, so it arrives with the transport. Tessera's own note
 * still points at {@code jmouse-ai-spring-boot}, which is where it used to live — Identity needs that
 * artifact for nothing, and adding it would drag an entire AI stack, its tables and its migrations into
 * an authorization server that has no AI feature.
 *
 * <p>⚠️ For the same reason, do not reach for a {@code @RequestScope} bean from a tool handler: there
 * is no request scope on this thread, and an {@code ObjectProvider} of one hands back a proxy that is
 * never null and fails on first use.
 */
@Component
@RequiredArgsConstructor
public class IdentityCallerResolver implements CallerResolver {

    private final IdentityUserRepository accounts;

    @Override
    public CallerIdentity resolve() {
        Jwt          token   = currentToken();
        IdentityUser account = accounts.findById(token.getSubject())
            .orElseThrow(() -> new ToolRefusedException(RefusalReason.NO_CALLER,
                "This credential acts as an account that no longer exists. Nothing was changed."));

        return CallerIdentity.of(account.getId()).with(Map.of(
            CallerAttributes.CALLER_NAME, describe(account),
            CallerAttributes.SUBJECT_CLAIM, account.getId()));
    }

    /**
     * ⚠️ The {@code cid} claim is required even though nothing here reads it. Its absence means the
     * token was not minted for this endpoint, and the filter chain has already refused such a token —
     * this is the second door, and it is shut for the same reason the first one is.
     */
    private Jwt currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object         principal      = authentication == null ? null : authentication.getPrincipal();

        if (principal instanceof Jwt token
            && token.getClaimAsString(JwtClaimNames.SUB) != null
            && token.getClaimAsString(McpCredentialService.CONNECTION_CLAIM) != null) {

            return token;
        }

        throw new ToolRefusedException(RefusalReason.NO_CALLER,
            "Nothing was authenticated, so there is nobody to run this as. If a credential was "
            + "presented, it was not one issued for this endpoint.");
    }

    private static String describe(IdentityUser account) {
        String displayName = account.getDisplayName();

        return displayName == null || displayName.isBlank() ? account.getEmail() : displayName;
    }
}
