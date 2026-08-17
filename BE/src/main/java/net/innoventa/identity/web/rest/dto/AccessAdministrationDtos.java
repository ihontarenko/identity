package net.innoventa.identity.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import net.innoventa.identity.domain.IdentityUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What the access screen reads and writes.
 *
 * <p>One file rather than nine, because these are one screen's vocabulary and splitting them costs a
 * reader eight files to learn what a single page shows.
 *
 * <p>⚠️ <strong>A role is addressed by NAME, not by an identifier.</strong> The engine stores one that
 * way and a name is what the policy document writes, so exposing the surrogate key would hand the
 * client a second way to say the same thing.
 *
 * <h2>⚠️ Shorter than Tessera's, and every absence is a floor Identity does not have</h2>
 *
 * <p>There is no {@code projectId} on any request and no {@code ProjectRef} in any view. Tessera's
 * screen has to ask "at which project" on every write because its roles are handed out there; here the
 * only place a grant can sit is the installation, so asking would be a control with one option.
 */
public final class AccessAdministrationDtos {

    private AccessAdministrationDtos() {
    }

    /**
     * One permission this build knows about.
     *
     * @param description what the policy document says it means. Never null in a build that started —
     *                    {@code DeclaredPolicyValidator} refuses a boot where the two lists disagree
     */
    public record PermissionView(String name, String description) {}

    /**
     * A role, and everything it carries.
     *
     * @param assignableAt the widest scope it may be handed out at. ⚠️ Read-only: it comes from the
     *                     policy document, and a field that guards against a mistake must not be
     *                     editable by whoever is making it
     * @param declared     whether {@code policy/identity.jmp} declares this role. ⚠️ The screen has to
     *                     say so — a bundle edited here is rewritten from the document the next time
     *                     the document changes, and an administrator should not discover that after a
     *                     deploy
     */
    public record RoleView(String name, String assignableAt, boolean declared, List<BundleEntryView> bundle) {}

    /**
     * One line of a role's bundle: a permission, and how far it reaches.
     *
     * @param carriedAt a scope <em>kind</em> and never an instance
     */
    public record BundleEntryView(@NotBlank String permission, @NotBlank String carriedAt) {}

    /** What a role should carry from now on — the whole bundle, never a difference. */
    public record SetBundleRequest(List<BundleEntryView> bundle) {}

    /** Give somebody a role, or take it back. Installation-wide, there being nowhere else. */
    public record AssignRoleRequest(@NotBlank String accountId, @NotBlank String roleName) {}

    /**
     * Hand one permission to one person, or take one away from them.
     *
     * <p>⚠️ <strong>A DENY beats every role that grants it, from anywhere.</strong> That is what this
     * exists for — taking something away from one person without editing the role that gives it to
     * everybody else — and it is why {@code reason} is not decoration: a denial nobody can explain is a
     * support ticket that outlives whoever wrote it.
     *
     * <p>It is also how {@link net.innoventa.identity.security.access.Permissions#DELETE_USER} is held
     * at all, since no role carries it.
     */
    public record GrantPermissionRequest(
        @NotBlank String accountId,
        @NotBlank String permission,
        boolean          allowed,
        // ⚠️ Deliberately NOT @NotBlank, though it is required. The service refuses a blank one with a
        // sentence explaining why a reason outlives whoever wrote it; bean validation would refuse it
        // first, with "Validation failed for object='grantPermissionRequest'. Error count: 1". Two
        // guards for one rule, and the worse message wins — so there is one guard, in the place that
        // can say something worth reading.
        String reason) {}

    /** Take back a personal allow or deny, which restores whatever a role was already saying. */
    public record RevokePermissionRequest(@NotBlank String accountId, @NotBlank String permission) {}

    /**
     * One person's hold on one role.
     *
     * @param account ⚠️ null where the grant outlived the account it was made to — a library table
     *                cannot foreign-key into this product's rows, so the screen shows the orphan rather
     *                than hiding it. Somebody has to be able to clear it
     */
    public record RoleHoldingView(
        AccountRef    account,
        String        roleName,
        String        scopeType,
        String        source,
        LocalDateTime since) {}

    /** One person's personal allow or deny. */
    public record DirectHoldingView(
        AccountRef    account,
        String        permission,
        boolean       allowed,
        String        scopeType,
        String        reason,
        LocalDateTime since) {}

    /**
     * A person named rather than identified.
     *
     * <p>A grant carries a row id, which is right for the engine and useless on a screen — nobody
     * recognises somebody by their primary key.
     */
    public record AccountRef(String id, String email, String displayName) {

        public static AccountRef from(IdentityUser identityUser) {
            return new AccountRef(
                identityUser.getId(), identityUser.getEmail(), identityUser.getDisplayName());
        }
    }

    /** Everything the screen shows at once, so it is one request rather than four. */
    public record AccessOverview(
        List<PermissionView>    permissions,
        List<RoleView>          roles,
        List<RoleHoldingView>   roleHoldings,
        List<DirectHoldingView> directHoldings,
        List<AccountRef>        accounts) {}
}
