package net.innoventa.identity.security.access.axis;

import net.innoventa.identity.security.access.AccessAxis;
import net.innoventa.identity.security.access.AccessReason;
import org.jmouse.access.AccessDecision;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.Subject;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.springframework.stereotype.Component;

/**
 * Axis 1 — who is asking.
 *
 * <p>⚠️ <strong>The name is the axis, not the product.</strong> In a service called Identity this reads
 * ambiguously, and it keeps the name every other product uses on purpose: this is
 * {@link AccessAxis#IDENTITY}, the question <em>is anybody there</em>, and it is the same class in
 * Tessera and in Innoventa. A local rename would make the one file a reader goes looking for the one
 * file they cannot find.
 *
 * <p>It refuses exactly one thing: nobody. That is the whole of it, and the smallness is the point — an
 * axis that also decided whether the caller were an administrator would be answering the next axis's
 * question with none of the next axis's information.
 *
 * <p><strong>It runs first so that a refusal names the outermost reason.</strong> Somebody who is not
 * signed in has to read <em>sign in</em>; told <em>you do not have permission</em> they would go and ask
 * an administrator for something no administrator can give them.
 */
@Component
public class IdentityAxis implements AccessAxisEvaluator {

    @Override
    public AccessAxis axis() {
        return AccessAxis.IDENTITY;
    }

    @Override
    public AccessDecision evaluate(Subject subject, String permission, AccessTarget target) {
        if (subject.isAuthenticated()) {
            return AccessDecision.allowed();
        }

        return AccessDecision.refused(
            AccessReason.UNAUTHENTICATED,
            "You need to be signed in to do this.");
    }
}
