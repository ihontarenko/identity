package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.access.CallerPermissions;
import net.innoventa.identity.security.access.IdentityScope;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.AccessOverview;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.AccountReference;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.BundleEntryView;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.DirectHoldingView;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.PermissionView;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.RoleHoldingView;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.RoleView;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessDisclosure;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyPermissionDeclaration;
import org.jmouse.access.policy.model.PolicyRole;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The screen behind {@link Permissions#ADMINISTER_ACCESS} — what the roles carry, and who holds them.
 *
 * <p><strong>It reads rows and writes rows.</strong> {@code policy/identity.jmp} is what a fresh
 * installation is born with; from the first start onwards the tables are the only thing the engine
 * consults, so a bundle edited here changes authorization with no deploy. That is the point of the
 * screen and the reason it is behind a permission of its own.
 *
 * <h2>⚠️ Two things this deliberately does not do</h2>
 *
 * <ul>
 *   <li><strong>It does not create or delete roles.</strong> A role's name appears in
 *       {@code security.access.Roles} and in the seed, and one invented at runtime would be a name no
 *       code can hand out — a row that exists and grants nobody anything. Adding one is a document
 *       change and a constant.
 *   <li><strong>It does not move {@code assignableAt}.</strong> A field that guards against a mistake
 *       must not be editable by whoever is making it.
 * </ul>
 *
 * <h2>⚠️ What a re-seed does to an edit</h2>
 *
 * <p>{@code PolicySeeder} rewrites the bundle of every role the document declares whenever the rendered
 * document moves. So an edit here to a declared role survives until somebody changes the file, and then
 * does not. The screen has to say so — {@link RoleView#declared()} is what it says it with.
 *
 * <h2>⚠️ Why nothing here takes a place</h2>
 *
 * <p>Tessera's equivalent asks "at which project" on every write. Identity's floors are
 * {@code GLOBAL} and {@code SELF}: both roles are assignable installation-wide, and a personal grant at
 * {@code SELF} would mean "over their own rows", which nothing in this service resolves — there are no
 * access target resolvers, because no route names a place. So every write here is at
 * {@code GLOBAL}, and offering a choice would be a control with one option and a wrong answer.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAdministrationService {

    private final AccessAdministration     access;
    private final AccessDisclosure         disclosure;
    private final ScopeCatalog             scopes;
    private final PolicyDocument           document;
    private final IdentityUserRepository   accounts;
    private final CallerPermissions        callerPermissions;

    /** Installation-wide — the one place anything here is written. */
    private static final ScopeReference EVERYWHERE =
        ScopeReference.of(IdentityScope.GLOBAL, ScopeKind.NO_INSTANCE);

    /**
     * Everything the screen shows, in one request.
     *
     * <p>⚠️ <strong>It walks every holding in the installation</strong>, which is what
     * {@link AccessDisclosure} is for and why it is a separate port the engine may not hold. It is a
     * disclosure surface: reading it is knowing who can do what, which is exactly the thing a
     * permission of its own exists to govern.
     */
    public AccessOverview overview() {
        Map<String, AccountReference> whoIsWho = accounts.findAll().stream()
            .collect(Collectors.toMap(IdentityUser::getId, AccountReference::from));

        return new AccessOverview(
            permissions(),
            roles(),
            roleHoldings(whoIsWho),
            directHoldings(whoIsWho),
            whoIsWho.values().stream()
                .sorted(Comparator.comparing(AccountReference::email, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    /**
     * Which roles each account holds, for a screen that is <em>about</em> accounts rather than about
     * access — the register (ID-8).
     *
     * <h2>⚠️ Empty for a caller who may not see it, and that is the whole of the disclosure rule</h2>
     *
     * <p>The register is gated on {@link Permissions#READ_USER}. Who holds what is gated on
     * {@link Permissions#ADMINISTER_ACCESS} — a separate power, and a disclosure surface in its own
     * right. Returning this to anybody who may merely read the register would widen {@code user:read}
     * to include everybody's grants, silently, through a column somebody added for convenience.
     *
     * <p>So it answers with what the caller is entitled to and <strong>nothing</strong> otherwise. It
     * does not refuse: the caller asked for the register, which they may have; they simply do not get
     * the extra column, and the interface renders it only when it is there.
     *
     * <p>⚠️ This is the resolution of a real contradiction between two tickets. ID-4 argued the users
     * list must not carry holdings at all, precisely because of the widening above; ID-8 asked for the
     * row to show what its holder holds. Both are right about their half — the answer is that the
     * column exists and is earned rather than assumed.
     */
    public Map<String, List<String>> rolesHeldByAccount() {
        if (!callerPermissions.ofCaller().contains(Permissions.ADMINISTER_ACCESS)) {
            return Map.of();
        }

        return disclosure.roleHoldings().stream()
            .collect(Collectors.groupingBy(
                holding -> holding.subjectId(),
                Collectors.mapping(holding -> holding.roleName(), Collectors.toList())));
    }

    /**
     * What a role carries from now on.
     *
     * <p>⚠️ <strong>The whole bundle, never a difference.</strong> A screen that sent "add this one"
     * would have no way to express a removal that raced with another administrator's addition; sending
     * the set makes the last save win, visibly, which is what anybody editing a list expects.
     */
    @Transactional
    public RoleView setBundle(String roleName, List<BundleEntryView> bundle) {
        AccessAdministration.RoleView existing = requireRole(roleName);

        bundle.forEach(this::requireItCanMatchSomething);

        access.setBundle(roleName, bundle.stream()
            .map(entry -> new AccessAdministration.BundleEntry(entry.permission(), entry.carriedAt()))
            .toList());

        return viewOf(existing.name(), existing.assignableAt(), bundle);
    }

    // ── Who holds what ────────────────────────────────────────────────────────

    /**
     * Give somebody a role.
     *
     * <p>⚠️ <strong>The screen would be a viewer without this.</strong> Seeing that somebody is missing
     * a role and having to go elsewhere to fix it is being shown a problem and handed no tool.
     */
    @Transactional
    public void assign(String accountId, String roleName, String by) {
        requireAccount(accountId);
        requireRole(roleName);

        access.assign(accountId, roleName, EVERYWHERE, "DIRECT", by, null);
    }

    @Transactional
    public void unassign(String accountId, String roleName) {
        access.unassign(accountId, roleName, EVERYWHERE);
    }

    // ── What one person holds personally ──────────────────────────────────────

    /**
     * One permission handed to, or taken from, one person — over the top of whatever their roles say.
     *
     * <p>⚠️ <strong>A deny is the only way to take something away without editing the role that gives
     * it.</strong> It wins over every allow from anywhere, so this is the sharpest instrument on the
     * screen and the reason {@code reason} is required rather than optional.
     *
     * <p>⚠️ It is also the <em>only</em> way {@link Permissions#DELETE_USER} is ever held: no role
     * carries it, deliberately, so that "who can permanently delete an account here" is answered by a
     * list of named people rather than by expanding a role.
     */
    @Transactional
    public void grant(String accountId, String permission, boolean allowed, String reason, String by) {
        requireAccount(accountId);
        requireKnownPermission(permission);

        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException(
                "A personal grant needs a reason. It outlives whoever made it, and one nobody can "
                + "explain is a refusal somebody will spend an afternoon on a year from now.");
        }

        access.grant(accountId, permission, EVERYWHERE,
            allowed ? AccessAdministration.Effect.ALLOW : AccessAdministration.Effect.DENY,
            reason, by, null);
    }

    @Transactional
    public void ungrant(String accountId, String permission) {
        access.ungrant(accountId, permission, EVERYWHERE);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    private List<PermissionView> permissions() {
        Map<String, String> described = document.permissions().stream()
            .collect(Collectors.toMap(
                PolicyPermissionDeclaration::name,
                declaration -> declaration.description() == null ? "" : declaration.description(),
                (first, second) -> first));

        return Permissions.all().stream()
            .map(name -> new PermissionView(name, described.getOrDefault(name, "")))
            .toList();
    }

    private List<RoleView> roles() {
        return access.roles().stream()
            .map(role -> viewOf(role.name(), role.assignableAt(), role.bundle().stream()
                .map(entry -> new BundleEntryView(entry.permission(), entry.scopeType()))
                .toList()))
            .sorted(Comparator.comparing(RoleView::name))
            .toList();
    }

    private List<RoleHoldingView> roleHoldings(Map<String, AccountReference> whoIsWho) {
        return disclosure.roleHoldings().stream()
            .map(holding -> new RoleHoldingView(
                whoIsWho.get(holding.subjectId()),
                holding.roleName(),
                holding.at().type().name(),
                holding.grantedBy(),
                holding.since()))
            .sorted(byPersonThenRole())
            .toList();
    }

    private List<DirectHoldingView> directHoldings(Map<String, AccountReference> whoIsWho) {
        return disclosure.directHoldings().stream()
            .map(holding -> new DirectHoldingView(
                whoIsWho.get(holding.subjectId()),
                holding.permission(),
                holding.allowed(),
                holding.at().type().name(),
                holding.reason(),
                holding.since()))
            .toList();
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private IdentityUser requireAccount(String accountId) {
        return accounts.findById(accountId)
            .orElseThrow(() -> new UsernameNotFoundException("No account found for id: " + accountId));
    }

    private AccessAdministration.RoleView requireRole(String roleName) {
        return access.roles().stream()
            .filter(role -> role.name().equals(roleName))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException(
                "There is no role called '" + roleName + "'. The roles are "
                + access.roles().stream()
                    .map(AccessAdministration.RoleView::name)
                    .collect(Collectors.joining(", ")) + "."));
    }

    private void requireKnownPermission(String permission) {
        if (!Permissions.all().contains(permission)) {
            throw new BusinessRuleViolationException(
                "'" + permission + "' is not a permission this build knows about, so granting it "
                + "would confer nothing and denying it would refuse nothing.");
        }
    }

    /**
     * ⚠️ A bundle entry naming a permission nobody declares, or a scope nobody registers, is refused at
     * the write rather than found later.
     *
     * <p>It would otherwise be stored, read back onto the screen, and confer nothing — the exact failure
     * {@code DeclaredPolicyValidator} exists to stop in the document, arriving through the editor
     * instead.
     */
    private void requireItCanMatchSomething(BundleEntryView entry) {
        requireKnownPermission(entry.permission());

        if (scopes.byName(entry.carriedAt()).isEmpty()) {
            throw new BusinessRuleViolationException(
                "'" + entry.carriedAt() + "' is not a scope this build registers. The scopes are "
                + registeredScopes() + ".");
        }
    }

    /**
     * ⚠️ <strong>{@code all()}, never {@code floors()}.</strong> A catalogue's "floors" are the scopes
     * that are <em>places</em> — the ones a target can name an instance of — and Identity has none of
     * those: {@code GLOBAL} is the widest scope and {@code SELF} is own-rows. So {@code floors()}
     * returns an empty list here and the refusal read <em>"The scopes are ."</em>, which is a sentence
     * that fails exactly when somebody needs it.
     *
     * <p>Tessera's version says {@code floors()} and reads correctly only because it happens to have
     * {@code @PROJECT}. The method is not "what is registered"; it is a subset that is usually
     * non-empty.
     */
    private String registeredScopes() {
        return scopes.all().stream().map(ScopeKind::name).collect(Collectors.joining(", "));
    }

    /** Whether {@code policy/identity.jmp} declares this role — see the class note on what a re-seed does. */
    private RoleView viewOf(String name, String assignableAt, List<BundleEntryView> bundle) {
        Set<String> declared = document.roles().stream()
            .map(PolicyRole::name)
            .collect(Collectors.toSet());

        return new RoleView(name, assignableAt, declared.contains(name), bundle);
    }

    /**
     * ⚠️ Sorted by a name that may be absent: a grant can outlive the account it was made to, because a
     * library table cannot foreign-key into this product's rows. The screen shows those orphans — they
     * are exactly what somebody has to clear — so the comparator has to survive one.
     */
    private static Comparator<RoleHoldingView> byPersonThenRole() {
        Function<RoleHoldingView, String> byPerson =
            holding -> holding.account() == null ? "" : String.valueOf(holding.account().email());

        return Comparator.comparing(byPerson, Comparator.nullsFirst(String::compareToIgnoreCase))
            .thenComparing(RoleHoldingView::roleName);
    }
}
