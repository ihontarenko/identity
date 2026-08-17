package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.el.PolicyWriter;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessAdministration.BundleEntry;
import org.jmouse.access.jpa.AccessAdministration.Change;
import org.jmouse.access.jpa.AccessAdministration.Effect;
import org.jmouse.access.jpa.AccessAdministration.RoleView;
import org.jmouse.access.jpa.PolicyRevisions;
import org.jmouse.access.jpa.PolicyRevisions.Revision;
import org.jmouse.access.policy.AccessPolicy;
import org.jmouse.access.policy.AccessPolicy.BoundAssignment;
import org.jmouse.access.policy.AccessPolicy.BoundSubject;
import org.jmouse.access.policy.PolicyBinder;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyRole;
import org.jmouse.access.spi.BundledPermission;
import org.jmouse.access.spi.DirectGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes what {@code policy/identity.jmp} declares into the tables that answer for it.
 *
 * <p>This is the step where a grant stops having two possible homes. The document could be a second
 * {@code GrantStore} the engine read beside the rows — the library offers exactly that, and
 * {@link net.innoventa.identity.config.AccessStorageConfiguration} refuses it. Instead the file is
 * read once, at a start whose rendered form has moved, and turned into rows; after which the access
 * screen edits the same fact the engine reads, and there is nothing to reconcile.
 *
 * <h2>⚠️ The ledger is {@link PolicyRevisions}, not a bootstrap-records table</h2>
 *
 * <p>The ticket described "a per-step ledger", which in Tessera is a whole bootstrap mechanism — an
 * interface, a runner, a table and a migration — carried by that product for several steps. Identity
 * has exactly one such step and no bootstrap package, and <strong>the library already ships a table
 * for precisely this fact</strong>: {@code access_policy_revisions}, migrated by
 * {@code jmouse-access-jpa}, holding the source of each version that has been in force. Comparing
 * against {@link PolicyRevisions#inForce()} is the same ledger with nothing to build and nothing to
 * migrate, and it leaves behind the history a "what was in force in March" screen would want.
 *
 * <p>⚠️ <strong>The comparison is the RENDERED document, never the file text.</strong> A rendering is
 * canonical, so reformatting the file, re-indenting a block or rewriting a comment is not a policy
 * change and must not read as one — which is what re-running the seed on every whitespace edit would
 * mean, and re-running undoes deliberate edits made on the access screen.
 *
 * <p>⚠️ <strong>The one thing this comparison does not catch</strong> is a bundle written with a
 * {@code namespace:*} wildcard: what it expands to depends on the permission catalogue, so adding a
 * permission would change the bundle's meaning without moving the document. Tessera folds its
 * catalogue into the checksum for exactly this reason. <strong>This document declares no wildcard and
 * is not meant to</strong> — the day one is written here, the catalogue has to join the comparison, or
 * the new permission silently never reaches the role.
 *
 * <h2>⚠️ Bound, not raw</h2>
 *
 * <p>{@link AccessPolicy} carries bundles with scopes already resolved to references. Expanding them
 * here would be a second implementation of {@link PolicyBinder}'s, and the two would drift.
 *
 * <h2>⚠️ An {@link ApplicationRunner}, not an {@code InitializingBean}</h2>
 *
 * <p>Unlike {@link DeclaredPolicyValidator}, which only compares two lists in memory, this writes rows
 * — so it waits until the context is complete rather than doing database work while beans are still
 * being created. A runner that throws still fails the application, so the failure is as loud either
 * way.
 *
 * <p>⚠️ <strong>The cost is a window: Tomcat is listening before this runs.</strong> A runner executes
 * after the web server starts, so for a fraction of a second the port is open on an installation whose
 * policy has not been seeded. That is survivable rather than ignored — readiness only becomes
 * {@code ACCEPTING_TRAFFIC} on {@code ApplicationReadyEvent}, which fires <em>after</em> runners, so
 * anything routing by the probe never reaches that window. Locally it is visible in the log as
 * "Started IdentityApplication" appearing above a seeding failure, which reads worse than it is.
 */
@Component
@RequiredArgsConstructor
@Order(PolicySeeder.SEEDS_BEFORE_ANYTHING_USES_A_ROLE)
public class PolicySeeder implements ApplicationRunner {

    /**
     * ⚠️ <strong>Explicit, because leaving it to the default is what broke.</strong>
     *
     * <p>{@link AdminRoleHandover} must run after this one — it assigns roles this class mints. It
     * asked for {@code Integer.MAX_VALUE} to be "last", which is <em>exactly</em> the value an
     * un-annotated runner already has ({@link Ordered#LOWEST_PRECEDENCE}). Two equal orders are a tie,
     * ties fall back to bean discovery order, and {@code AdminRoleHandover} sorts before
     * {@code PolicySeeder} alphabetically — so the handover ran first and the boot died with
     * <em>"There is no role called 'GLOBAL_ACCESS_ADMINISTRATOR'"</em>.
     *
     * <p>Both ends are stated now, and the other end is written as this constant plus one, so the
     * relationship survives somebody changing this number.
     */
    static final int SEEDS_BEFORE_ANYTHING_USES_A_ROLE = Ordered.LOWEST_PRECEDENCE - 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicySeeder.class);

    /**
     * The provenance {@link AccessAdministration} keeps deliberately open, so that a later revocation
     * of somebody's grant leaves a seeded assignment alone.
     */
    private static final String ASSIGNED = "SEEDED";

    private static final String BY = "bootstrap";

    private static final String AUTHOR_LABEL = "Seeded from policy/identity.jmp";

    private final PolicyDocument       shipped;
    private final PolicyBinder         binder;
    private final ScopeCatalog         scopes;
    private final AccessAdministration access;
    private final PolicyRevisions      revisions;
    private final SubjectHandles       handles;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String            rendered = PolicyWriter.write(shipped);
        Optional<Revision> inForce = revisions.inForce();

        if (inForce.filter(revision -> rendered.equals(revision.source())).isPresent()) {
            LOGGER.debug("🔒 Policy '{}' is already in force at version {} — nothing seeded.",
                shipped.name(), inForce.get().version());
            return;
        }

        AccessPolicy policy = binder.bind(shipped);

        int written = seedRoles(policy) + seedSubjects(policy);

        Revision saved = revisions.save(rendered, note(inForce), BY, AUTHOR_LABEL, null,
            policy.roles().size(), policy.subjects().size());

        LOGGER.info("🔑 Policy '{}' seeded as version {} — {} role(s), {} subject(s), {} row(s) written.",
            shipped.name(), saved.version(), policy.roles().size(), policy.subjects().size(), written);
    }

    private String note(Optional<Revision> inForce) {
        return inForce.isEmpty()
            ? "First seed of the shipped policy document"
            : "The shipped policy document changed since version " + inForce.get().version();
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    /**
     * ⚠️ {@code assignableAt} comes from the document and from nowhere else.
     *
     * <p>It is the widest scope a role may be handed out at. Deriving it from the widest scope in the
     * bundle would be right by accident for these two and undefined for a role that bundles nothing —
     * so the grammar states it, and a role that does not state it is skipped loudly rather than seeded
     * at a guess.
     */
    private int seedRoles(AccessPolicy policy) {
        Set<String> alreadyDefined = access.roles().stream()
            .map(RoleView::name)
            .collect(Collectors.toSet());

        int written = 0;

        for (PolicyRole declared : shipped.roles()) {
            Optional<ScopeKind> assignableAt = assignableScopeOf(declared);

            if (assignableAt.isEmpty()) {
                continue;
            }

            // ⚠️ This runs on a re-seed as often as on a first seed. `defineRole` refuses a name that
            // exists — rightly, because two roles by one name are two answers to one question — so the
            // row is created only where there is none, and the bundle is written every time. That is
            // what makes a re-seed mean "bring the tables back in line with the file" rather than "fail".
            if (!alreadyDefined.contains(declared.name())) {
                access.defineRole(declared.name(), assignableAt.get());
            }

            access.setBundle(declared.name(), bundleOf(declared.name(), policy.roles()));

            written++;
        }

        return written;
    }

    private Optional<ScopeKind> assignableScopeOf(PolicyRole role) {
        if (!role.statesWhereItMayBeAssigned()) {
            LOGGER.error("Role '{}' does not say where it may be assigned, so it is NOT seeded. Write "
                         + "'declare role {} assignable @SCOPE {{ … }}' — without it there is nothing "
                         + "to stop the role being handed out at any scope at all.",
                role.name(), role.name());
            return Optional.empty();
        }

        Optional<ScopeKind> kind = scopes.byName(role.assignableAt());

        if (kind.isEmpty()) {
            LOGGER.error("Role '{}' is assignable at '@{}', which is not a scope this build registers. "
                         + "It is NOT seeded.", role.name(), role.assignableAt());
        }

        return kind;
    }

    private List<BundleEntry> bundleOf(String roleName, Map<String, List<BundledPermission>> bound) {
        return bound.getOrDefault(roleName, List.of()).stream()
            .map(entry -> new BundleEntry(entry.permission(), entry.carriedAt().name()))
            .toList();
    }

    // ── Subjects ──────────────────────────────────────────────────────────────

    /**
     * Whatever the document assigns by hand — to the account the handle actually names.
     *
     * <p>⚠️ <strong>A handle nobody answers to stops the boot</strong>, and that is a deliberate
     * difference from Tessera, where the same situation is only a warning. Tessera's members are
     * provisioned on first sign-in, so an owner who has not signed in yet is an ordinary state there.
     * Identity's accounts are rows that exist before the first boot — the seeded administrator arrives
     * in {@code V000001} — so a handle naming nobody here is a misconfigured property, and the only
     * thing it can produce is an installation whose access screen nobody can open. Better to say so at
     * startup than to discover it at the first refusal.
     */
    private int seedSubjects(AccessPolicy policy) {
        int written = 0;

        for (Map.Entry<String, BoundSubject> held : policy.subjects().entrySet()) {
            String       handle  = held.getKey();
            IdentityUser account = handles.resolve(handle).orElseThrow(() -> nobodyAnswersTo(handle));

            written += seedOneSubject(account.getId(), held.getValue());
        }

        return written;
    }

    private IllegalStateException nobodyAnswersTo(String handle) {
        return new IllegalStateException(
            "The policy document '" + shipped.name() + "' assigns to '" + handle + "', and no account "
            + "in this installation answers to it — not as a row id, and not as a LOCAL email address. "
            + "Nothing was seeded. Every grant here is keyed on the row id, so writing the handle "
            + "through unresolved would have produced an assignment that parses, appears on the access "
            + "screen and grants absolutely nothing. Point 'identity.bootstrap.owner' at an account "
            + "that exists.");
    }

    private int seedOneSubject(String subjectId, BoundSubject held) {
        int written = 0;

        for (BoundAssignment assignment : held.roles()) {
            // ⚠️ Counted by what the port says it changed, not by what was attempted. On a re-seed every
            // one of these is already there and reports nothing, and a step claiming it wrote eight rows
            // when it wrote none is a log line that makes the ledger unreadable.
            Change assigned = access.assign(
                subjectId, assignment.roleName(), assignment.at(), ASSIGNED, BY, null);

            if (assigned.changed()) {
                written++;
            }
        }

        for (DirectGrant grant : held.grants()) {
            Change change = access.grant(subjectId, grant.permission(), grant.at(),
                grant.allowed() ? Effect.ALLOW : Effect.DENY,
                "Seeded from the policy document", BY, null);

            if (change.changed()) {
                written++;
            }
        }

        return written;
    }
}
