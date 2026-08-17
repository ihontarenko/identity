package net.innoventa.identity.mcp.tool;

import net.innoventa.identity.mcp.OnMcpConfigured;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.IdentityScope;
import net.innoventa.identity.security.access.Permissions;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.Subject;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.InvocationScope;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.springframework.stereotype.Component;

/**
 * What an action costs, asked of the same engine the screens ask.
 *
 * <h2>⚠️ This is the epic's invariant, and it is one line of code</h2>
 *
 * <p>Every tool declares the permission its equivalent button declares, and this resolves it against
 * the same subject, from the same rows, through the same {@link EffectivePermissionsResolver} that
 * refuses a request to {@code /api/admin/users}. There is no second path into an action and no second
 * place a permission is checked — <strong>an agent holds exactly what the person driving it holds,
 * because it is asking the same question about the same person.</strong>
 *
 * <p>It follows that a tool being <em>present</em> says nothing about whether it will work. That is
 * correct and deliberate: hiding tools a caller cannot use would make the tool list a disclosure of
 * who holds what, which is precisely the thing {@code access:administer} exists to gate.
 *
 * <h2>Installation-wide, because there is nowhere else to ask about</h2>
 *
 * <p>Tessera asks twice — <em>anywhere at all</em>, then <em>at this project</em> — because a person
 * may transition issues in one project and not the next. Identity has {@code GLOBAL} and {@code SELF}
 * and no route that names a place, so both questions are the same question and answering them
 * differently would be inventing a distinction the model does not have.
 */
@OnMcpConfigured
@Component
@RequiredArgsConstructor
public class IdentityToolAuthorizer implements ToolAuthorizer {

    /** Installation-wide — the one place anything here is decided. */
    private static final ScopeReference EVERYWHERE =
        ScopeReference.of(IdentityScope.GLOBAL, ScopeKind.NO_INSTANCE);

    private final EffectivePermissionsResolver effectivePermissions;

    @Override
    public boolean permits(CallerIdentity caller, ToolAction action) {
        return holds(caller, action.requiredPermission());
    }

    @Override
    public boolean permitsInScope(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        return holds(caller, action.requiredPermission());
    }

    /**
     * Why the permission is missing, where the plain refusal would send somebody to debug the wrong
     * thing.
     *
     * <p>⚠️ <strong>On a database whose policy has never been seeded, every tool refuses at once and
     * each refusal names a permission.</strong> That sentence sends a reader to the token, the
     * credential and the caller resolver, all of which are fine. The cause is structural — there are no
     * grant rows at all — and Tessera has the identical trap recorded. Somebody holding
     * {@code user:read} is in an ordinary missing-permission situation and gets the plain refusal.
     */
    @Override
    public String refusalAdvice(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        if (holds(caller, Permissions.READ_USER)) {
            return null;
        }

        return "This caller holds nothing at all, which in Identity usually means the policy has never "
               + "been seeded rather than that a grant was made wrongly — check that `access_roles` has "
               + "rows before reading any Java. If it does, somebody who administers access has to "
               + "grant this caller a role on the access screen.";
    }

    private boolean holds(CallerIdentity caller, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }

        // ⚠️ `actsOnBehalfOfId`, not `callerId`, even though Identity's are always equal. They are equal
        // because this product has no agent sub-accounts — not because the distinction is meaningless —
        // and writing the one that means WHOSE authority is in play is what keeps this correct if that
        // ever changes. Tessera's equivalent got this wrong once and silently ignored every restriction.
        Subject subject = Subject.of(caller.actsOnBehalfOfId(), caller.actsOnBehalfOfId());

        return effectivePermissions.resolveInstallationWide(subject).contains(permission);
    }
}
