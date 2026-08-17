package net.innoventa.identity.security.access.axis;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.AccessAxis;
import net.innoventa.identity.security.access.AccessReason;
import org.jmouse.access.AccessDecision;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.Subject;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Axis 2 — may <em>this person</em> do <em>this</em>?
 *
 * <p>The last axis, and in this service the whole of the answer. Ownership is not a comparison of two
 * identifiers here: a subject holding a permission at
 * {@link net.innoventa.identity.security.access.IdentityScope#SELF} holds it over their own account,
 * and the covering chain decides that rather than an {@code equals} somebody wrote in a service.
 *
 * <p>⚠️ <strong>Shorter than Tessera's, and the missing half is deliberate.</strong> Tessera's version
 * distinguishes "outside the project" from "inside it without this power", because a person who is not
 * a member must not learn a project exists. <strong>Identity has no such place to be outside of</strong>
 * — the register is one list, gated whole — so there is nothing to hide behind a 404 and no branch here
 * pretending otherwise.
 *
 * <p>⚠️ <strong>A blank permission allows.</strong> A route asking for access without naming a
 * permission is saying <em>a signed-in caller and nothing more</em>, and axis 1 has already answered
 * that. Refusing here would make the bare form unusable and push every such route back out of the
 * engine.
 *
 * <p>⚠️ <strong>What this axis never sees is what deny-wins is worth.</strong> The subtraction runs
 * inside {@link EffectivePermissionsResolver}, at every level, before the set reaches here — which is
 * why a personal DENY beats the role that grants it without this class knowing either exists.
 */
@Component
@RequiredArgsConstructor
public class PermissionAxis implements AccessAxisEvaluator {

    private final ObjectProvider<EffectivePermissionsResolver> effectivePermissions;

    @Override
    public AccessAxis axis() {
        return AccessAxis.PERMISSION;
    }

    @Override
    public AccessDecision evaluate(Subject subject, String permission, AccessTarget target) {
        if (permission == null || permission.isBlank()) {
            return AccessDecision.allowed();
        }

        EffectivePermissions effective = effectivePermissions.getObject().resolve(subject, target);

        if (effective.contains(permission)) {
            return AccessDecision.allowed();
        }

        // ⚠️ Names what is missing and never who holds it. Pointing at a person would disclose the
        // register to a caller who cannot read it, which is exactly what `user:read` exists to gate.
        return AccessDecision.refused(
            AccessReason.NO_PERMISSION,
            "You do not have permission to do this (" + permission + "). Somebody who administers "
            + "access has to give it to you.");
    }
}
