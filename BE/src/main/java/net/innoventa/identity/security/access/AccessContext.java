package net.innoventa.identity.security.access;

import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.Subject;
import org.jmouse.access.VisibilityScope;
import org.jmouse.access.spi.ResolutionCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What this request has already asked, so that it is never asked twice.
 *
 * <p>Two things ride here, and both are the same shape of saving: the answer is a property of the
 * <em>reader</em>, not of the row, so anything resolving inside a loop asks one question many times and
 * gets one answer many times.
 *
 * <ul>
 *   <li><strong>Effective permissions.</strong> The admin screen asks about several permissions to
 *       decide which controls to render, and the endpoints behind it ask again; all of it resolves one
 *       set, once.
 *   <li><strong>Visibility scopes.</strong> Which places a reader may see under one permission — asked
 *       once per listing rather than once per row.
 *   <li><strong>Who is asking.</strong> Resolving the subject costs a lookup of the account row, and
 *       the engine asks for it once per guarded call rather than once per request — so without this
 *       slot a screen behind three guarded calls would read the same row three times.
 * </ul>
 *
 * <p>{@link #resolutionCount()} exists so that a resolution moved inside a loop can be caught as a
 * number rather than as a slow screen.
 */
@Component
@RequestScope
public class AccessContext implements ResolutionCache {

    private final Map<PermissionQuestion, EffectivePermissions> permissionSets   = new HashMap<>();
    private final Map<VisibilityQuestion, VisibilityScope>      visibilityScopes = new HashMap<>();

    private Subject subject         = null;
    private int     resolutionCount = 0;

    /**
     * This request's cache, or {@code null} where there is no request to have one.
     *
     * <p>⚠️ <strong>The test is {@link RequestContextHolder} and not {@code getIfAvailable() == null},
     * because the obvious version does not work and fails late.</strong> {@code @RequestScope} defaults
     * to {@code proxyMode = TARGET_CLASS}, so the provider hands back a CGLIB proxy whatever the thread
     * is — never null. The null check therefore always passes, and the failure lands on the first
     * method call as {@code ScopeNotActiveException: Scope 'request' is not active for the current
     * thread}, from inside the engine, on whichever off-request caller happened to run first.
     *
     * <p>Static and shared so that the two beans bridging singletons to this scope ask the question the
     * same way. There is no third way to ask it correctly.
     */
    public static AccessContext current(ObjectProvider<AccessContext> request) {
        return RequestContextHolder.getRequestAttributes() == null ? null : request.getIfAvailable();
    }

    /**
     * Who is asking, resolved at most once per request.
     *
     * <p>Not part of {@link ResolutionCache} — the engine never asks this object for a subject. It is
     * {@code CurrentUserSubject} that does, and this is where the answer is kept so that resolving it
     * three times in one request reads the account row once.
     */
    public Subject subject(Supplier<Subject> loader) {
        if (subject == null) {
            subject = loader.get();
        }

        return subject;
    }

    /**
     * The effective set for one subject at one scope chain, resolved at most once per request.
     *
     * <p>Keyed on the chain rather than on the subject alone — the same person legitimately has
     * different answers at different scopes.
     */
    @Override
    public EffectivePermissions permissions(
        String                         subjectId,
        List<ScopeReference>           chain,
        Supplier<EffectivePermissions> loader) {

        return permissionSets.computeIfAbsent(new PermissionQuestion(subjectId, chain), question -> {
            resolutionCount++;
            return loader.get();
        });
    }

    @Override
    public VisibilityScope visibility(
        String                    subjectId,
        String                    permission,
        Supplier<VisibilityScope> loader) {

        return visibilityScopes.computeIfAbsent(
            new VisibilityQuestion(subjectId, permission), question -> {
                resolutionCount++;
                return loader.get();
            });
    }

    /** How many resolutions this request actually performed. */
    public int resolutionCount() {
        return resolutionCount;
    }

    private record PermissionQuestion(String subjectId, List<ScopeReference> chain) {}

    private record VisibilityQuestion(String subjectId, String permission) {}
}
