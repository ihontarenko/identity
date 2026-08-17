package net.innoventa.identity.config;

import net.innoventa.identity.security.access.AccessAxis;
import net.innoventa.identity.security.access.AccessReason;
import net.innoventa.identity.security.access.IdentityScope;
import net.innoventa.identity.security.access.Permissions;
import org.jmouse.access.AccessEngine;
import org.jmouse.access.AxisCatalog;
import org.jmouse.access.AxisKind;
import org.jmouse.access.EffectivePermissionsResolver;
import org.jmouse.access.EngineRefusals;
import org.jmouse.access.PermissionCatalog;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.VisibilityScopeResolver;
import org.jmouse.access.axis.AccessAxisEvaluator;
import org.jmouse.access.spi.AccessTargetRegistry;
import org.jmouse.access.spi.AccessTargetResolver;
import org.jmouse.access.spi.GrantStore;
import org.jmouse.access.spi.ResolutionCache;
import org.jmouse.access.spi.ScopeHierarchy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Where Identity tells the authorization engine what its words are.
 *
 * <p>The engine holds the mechanism and none of the vocabulary: it knows that scopes nest and that axes
 * run in order, and it learns which scopes and which axes exist only from here. That separation is what
 * makes adopting it a matter of registering three catalogues rather than of bending a service into
 * another product's shape — Identity's ladder is two floors where Tessera's is three, and nothing above
 * this file knows the difference.
 *
 * <p><strong>Each catalogue validates what it is handed</strong> and refuses to build on a vocabulary
 * that does not hold together — a duplicate rank, a missing widest scope, an axis nothing answers. A
 * context that starts is a context whose authorization model was checked.
 *
 * @see AccessStorageConfiguration for the storage half — the rows, and the ports that write them
 */
@Configuration
public class AccessVocabularyConfiguration {

    /**
     * Identity's two scopes: the installation, and a person's own rows.
     *
     * <p>Handed over as {@link IdentityScope#values()} rather than listed again, because the enum's
     * declaration order <em>is</em> the containment order and writing the list twice is how the two
     * drift apart.
     */
    @Bean
    public ScopeCatalog scopeCatalog() {
        return new ScopeCatalog(List.<ScopeKind>of(IdentityScope.values()));
    }

    /**
     * ⚠️ <strong>Flat, and saying so is more honest than leaving it out.</strong>
     *
     * <p>The hierarchy answers <em>which wider places contain this one</em>, and here the answer is
     * none: there are two floors and the wider one is the installation. Several beans default to
     * {@link ScopeHierarchy#flat()} when no bean exists, so this registration changes no behaviour —
     * what it changes is that a reader finds the answer here instead of inferring it from four separate
     * absences.
     */
    @Bean
    public ScopeHierarchy scopeHierarchy() {
        return ScopeHierarchy.flat();
    }

    /**
     * Identity's two axes, in the order a request is asked them.
     *
     * <p>Innoventa registers six because it has modules to switch off and capabilities to sell.
     * Registering those here would put links in the chain that always allow — a step every reader has
     * to be told to ignore. See {@link AccessAxis}.
     */
    @Bean
    public AxisCatalog axisCatalog() {
        return new AxisCatalog(List.<AxisKind>of(AccessAxis.values()));
    }

    /**
     * Every permission this build knows.
     *
     * <p>Read off {@link Permissions#all()} rather than listed again, for the reason the scope catalogue
     * is read off the enum: a second list beside the constants is a list that is one commit behind from
     * the day it is written.
     */
    @Bean
    public PermissionCatalog permissionCatalog() {
        return new PermissionCatalog(Permissions.all());
    }

    /**
     * The words for the two refusals the dispatcher raises itself.
     *
     * <p>Every other refusal comes out of an axis, and an axis is a bean of Identity's that already
     * knows how to say what it means.
     */
    @Bean
    public EngineRefusals engineRefusals() {
        return new EngineRefusals(AccessReason.NOT_FOUND_OR_HIDDEN, AccessReason.UNDECIDABLE_CALL);
    }

    // ── The engine itself ─────────────────────────────────────────────────────
    //
    // Declared here rather than component-scanned, because these classes live in `jmouse-access` and
    // carry no Spring annotations at all — which is the point of them living there. Wiring a library is
    // the application's job, and this is where Identity does it.

    /**
     * Every feature's answer to "where do my rows live", collected by type.
     *
     * <p>⚠️ <strong>Empty today, and that is the correct answer rather than a gap.</strong> A resolver
     * exists to turn a route's argument into a place, and neither of Identity's two scopes has a place
     * to name: the installation is everywhere, and a person's own rows are wherever they are.
     *
     * <p>Taken as an {@link ObjectProvider} rather than a {@code List} for exactly that reason — Spring
     * treats a collection with no candidates as an unsatisfied dependency and refuses to start, so
     * asking for the list directly would make "no resolvers" a startup failure instead of a fact.
     */
    @Bean
    public AccessTargetRegistry accessTargetRegistry(ObjectProvider<AccessTargetResolver<?>> resolvers) {
        return new AccessTargetRegistry(resolvers.stream().toList());
    }

    /**
     * @param grants ⚠️ <strong>the rows, and only the rows.</strong> The policy auto-configuration
     *               registers a store of its own over the document and would compose the two; that
     *               composition is refused in {@link AccessStorageConfiguration}, which claims the
     *               composed bean's name for the JPA store. Which of the two arrives here is the whole
     *               of "a grant lives in exactly one place"
     */
    @Bean
    public EffectivePermissionsResolver effectivePermissionsResolver(
        GrantStore      grants,
        ScopeCatalog    scopes,
        ResolutionCache cache) {

        // ⚠️ No ShareGrants: Identity has no share links. The resolver takes null for "this
        // installation grants nothing through a token", which is correct rather than degraded.
        return new EffectivePermissionsResolver(grants, scopes, cache, null);
    }

    /**
     * Which places a reader may see under one permission — resolved once per request, never per row.
     *
     * <p>What a listing filters on.
     */
    @Bean
    public VisibilityScopeResolver visibilityScopeResolver(
        GrantStore grants, ResolutionCache cache, ScopeHierarchy scopeHierarchy) {

        return new VisibilityScopeResolver(grants, cache, scopeHierarchy);
    }

    @Bean
    public AccessEngine accessEngine(
        List<AccessAxisEvaluator> axes,
        AxisCatalog               declaredAxes,
        AccessTargetRegistry      targets,
        VisibilityScopeResolver   visibilityScopes,
        EngineRefusals            refusals) {

        return new AccessEngine(axes, declaredAxes, targets, visibilityScopes, refusals);
    }
}
