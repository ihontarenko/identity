package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.Subject;
import org.jmouse.access.enforcement.CurrentSubject;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * What the caller may do, as a flat list of names — the one answer an interface needs.
 *
 * <h2>⚠️ Names, never roles</h2>
 *
 * <p>A screen that branched on {@code GLOBAL_USER_ADMINISTRATOR} would break the moment somebody is
 * granted {@code user:create} personally without the bundle — which is the entire point of having
 * personal grants, and the way {@link Permissions#DELETE_USER} is held by design, since it sits in no
 * role at all. So what leaves this service is the set of permissions, and a control is rendered from
 * the permission that backs it.
 *
 * <p>⚠️ It is also the reason this is a <em>disclosure</em> worth being deliberate about: it tells the
 * caller what the caller may do, and nothing whatever about anybody else.
 *
 * <h2>Installation-wide, because there is nowhere else to ask about</h2>
 *
 * <p>{@link EffectivePermissionsResolver#resolveInstallationWide} rather than a resolution against a
 * target: Identity's floors are {@code GLOBAL} and {@code SELF}, and no route names a place. A product
 * with a project or a category would have to ask this question once per place, and would get a
 * different answer each time.
 *
 * <p>Sorted, because this is read by people as often as by code, and an unordered set prints in a
 * different order on every start.
 */
@Component
@RequiredArgsConstructor
public class CallerPermissions {

    private final EffectivePermissionsResolver effectivePermissions;
    private final CurrentSubject               currentSubject;

    /** Everything the signed-in caller holds, installation-wide. Empty for a caller holding nothing. */
    public Set<String> ofCaller() {
        Subject subject = currentSubject.get();

        if (!subject.isAuthenticated()) {
            return Set.of();
        }

        return new TreeSet<>(effectivePermissions.resolveInstallationWide(subject).names());
    }
}
