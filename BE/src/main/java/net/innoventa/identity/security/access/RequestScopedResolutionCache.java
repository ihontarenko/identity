package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import org.jmouse.access.EffectivePermissions;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.VisibilityScope;
import org.jmouse.access.spi.ResolutionCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The engine's {@link ResolutionCache}, backed by the request.
 *
 * <p>{@link AccessContext} is the cache and is request-scoped; the resolvers that read it are
 * singletons in {@code jmouse-access} that know nothing about Spring. This is the one bean bridging the
 * two, and the whole of its content is the answer to "what if there is no request".
 *
 * <p><strong>No request means no caching, not no answer.</strong> A startup check, a scheduled job and
 * — from ID-10 — a protocol tool invocation all ask the engine questions off a servlet thread. Falling
 * back to the loader keeps them correct at the cost of asking twice, which is the right trade: a
 * singleton cache remembering across them would keep serving a revoked grant until restart.
 *
 * <p>⚠️ Whether there is a request is asked through {@link AccessContext#current}, and never by
 * null-checking the provider — see that method for why the obvious version silently always says yes.
 */
/*
 * @Primary because there are two beans of this type and only one of them is injectable: AccessContext
 * implements the interface as well — it is the store, after all — but it is @RequestScope, so a
 * singleton asking for a ResolutionCache must get this one. Without the annotation the context fails to
 * start with "expected single matching bean but found 2".
 */
@Component
@Primary
@RequiredArgsConstructor
public class RequestScopedResolutionCache implements ResolutionCache {

    private final ObjectProvider<AccessContext> request;

    @Override
    public EffectivePermissions permissions(
        String subjectId, List<ScopeReference> chain, Supplier<EffectivePermissions> loader) {

        AccessContext current = AccessContext.current(request);

        return current == null ? loader.get() : current.permissions(subjectId, chain, loader);
    }

    @Override
    public VisibilityScope visibility(
        String subjectId, String permission, Supplier<VisibilityScope> loader) {

        AccessContext current = AccessContext.current(request);

        return current == null ? loader.get() : current.visibility(subjectId, permission, loader);
    }
}
