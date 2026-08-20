package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.access.AccessReason;
import net.innoventa.identity.security.access.AccessRefusedException;
import net.innoventa.identity.security.access.CallerPermissions;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

/**
 * Backs the admin user-management panel. Kept separate from {@link AccountService} — that one is
 * self-service for whichever user owns the session, this one acts on an arbitrary target user on
 * an administrator's behalf.
 *
 * <p>⚠️ <strong>Nothing gates this class as a whole any more, and that is the change ID-4 made.</strong>
 * It used to be true that {@code SecurityConfiguration} refused every {@code /api/admin/**} call
 * without {@code ROLE_ADMIN} before it reached here, so one boolean covered every method below. That
 * matcher is gone: each route now declares the permission it actually needs, and they are four
 * different ones. A caller reaching {@link #deleteUser} has been asked a different question than one
 * reaching {@link #listUsers}.
 *
 * <p>Delete is a hard delete — no other table in this database references {@code identity_users}
 * yet (Moneta's {@code workspaces.owner_subject} is a separate database, referenced only by
 * string convention), and there's no audit requirement recorded anywhere that would call for a
 * soft-delete flag instead.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final IdentityUserRepository      identityUserRepository;
    private final PasswordEncoder             passwordEncoder;
    /**
     * ⚠️ Raising an account may hand it roles, so this service depends on the one that grants them —
     * and it is a real dependency rather than a convenience: the two writes have to share a
     * transaction, which two calls from a controller cannot do.
     */
    private final AccessAdministrationService accessAdministrationService;
    private final CallerPermissions           callerPermissions;

    public List<IdentityUser> listUsers() {
        return identityUserRepository.findAll().stream()
            .sorted(Comparator.comparing(IdentityUser::getCreatedAt).reversed())
            .toList();
    }

    /**
     * One account, by its identifier.
     *
     * <h2>⚠️ Why this exists beside {@link #listUsers()} rather than being a filter over it</h2>
     *
     * <p>A caller that wants one account and filters the register for it reads every row in the
     * installation to answer a question about one of them. That is invisible while the register is
     * small and stops being invisible exactly once, at a size nobody chose — and the fix is then a
     * change to whichever call site somebody happens to be looking at rather than to all of them.
     *
     * <p>It is also the honest shape: the question is <em>this account</em>, and a query that says so
     * can be answered by an index. Absence is {@link Optional#empty()} rather than an exception,
     * because the caller decides what a missing account means — the register renders nothing, the
     * protocol says there is nothing to act on.
     */
    public Optional<IdentityUser> findUser(String accountId) {
        return identityUserRepository.findById(accountId);
    }

    /**
     * Raises an account, with a password the administrator chose and the person must replace.
     *
     * <h2>⚠️ {@code LOCAL} only, and it is not a limitation</h2>
     *
     * <p>A {@code GOOGLE} or {@code GITHUB} row is created by {@code OAuth2AccountService} on that
     * provider's first sign-in, keyed on the email the provider asserts. Typing one in by hand would
     * produce a row that either duplicates the real one when it arrives or shadows it — and this
     * service has <strong>no account linking</strong>, a known and accepted gap, so the two would never
     * become one identity. There is nothing to choose, so nothing offers the choice.
     *
     * <p>⚠️ <strong>The uniqueness constraint is {@code (email, provider)}, so this legitimately
     * succeeds for an email that already exists as a Google identity</strong> — producing two accounts
     * with one address. That is occasionally what somebody means, so it is not refused here; the
     * interface warns at the moment it is done, which is far better than the discovery happening later.
     */
    @Transactional
    public IdentityUser createUser(
        String email, String displayName, String initialPassword, List<String> roles, String grantedBy) {

        String       trimmed = email == null ? "" : email.trim();
        List<String> wanted  = roles == null ? List.of() : roles;

        requireItMayHandOutRoles(wanted);

        identityUserRepository.findByEmailAndProvider(trimmed, Provider.LOCAL).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                "There is already a local account for " + trimmed + ".");
        });

        IdentityUser raised = identityUserRepository.save(IdentityUser.builder()
            .id(UUID.randomUUID().toString())
            .provider(Provider.LOCAL)
            .email(trimmed)
            .displayName(displayName)
            .hashedPassword(passwordEncoder.encode(initialPassword))
            .enabled(true)
            // ⚠️ The whole point of the feature: what the administrator typed is a way in, not a
            // credential they keep.
            .mustChangePassword(true)
            .build());

        // ⚠️ ONE TRANSACTION, and it is the whole of "not created without the roles". `assign` is
        // @Transactional too, and Spring's default propagation joins this one rather than opening a
        // second — so a role name nothing matches rolls the account back with it. An account that
        // exists holding rights nobody asked for, and a grant pointing at a row that was rolled back,
        // are both worse than either half failing.
        for (String role : wanted) {
            accessAdministrationService.assign(raised.getId(), role, grantedBy);
        }

        return raised;
    }

    /**
     * ⚠️ <strong>Naming a role needs {@code access:administer} as well as {@code user:create}, and the
     * check is here rather than on the route.</strong>
     *
     * <p>They are two separate powers and one request must not quietly combine them. The annotation on
     * the controller declares one permission, which is right — the route <em>is</em> "create a user" —
     * so the second, conditional power is asked for where the condition is known.
     *
     * <p>⚠️ <strong>It refuses, rather than creating the account without the roles.</strong> A partial
     * success on a request that named the rights is worse than a refusal, because the caller walks away
     * believing the rights are there.
     */
    private void requireItMayHandOutRoles(List<String> roles) {
        if (roles.isEmpty() || callerPermissions.ofCaller().contains(Permissions.ADMINISTER_ACCESS)) {
            return;
        }

        throw new AccessRefusedException(
            AccessReason.NO_PERMISSION,
            "Raising an account is one power and handing out roles is another. This request names "
            + String.join(", ", roles) + ", which needs " + Permissions.ADMINISTER_ACCESS
            + " as well — so nothing was created. Raise the account without roles, or ask for that "
            + "permission.");
    }

    /**
     * Sets somebody else's password — the account-recovery path until a real reset flow exists.
     *
     * <p>⚠️ <strong>Deliberately a human asking another human</strong>, rather than a self-service
     * link. It is also impersonation with a delay, which is why it asks for {@code user:password}
     * rather than being inherited from editing a profile: whoever holds it can sign in as anybody, one
     * reset later.
     *
     * <p>Sets the same flag, so the person is made to replace it — otherwise the administrator and the
     * account share a credential indefinitely.
     *
     * <p>⚠️ <strong>Refused against one's own account, and for a different reason than disable and
     * delete are.</strong> Those guard against locking the installation out of itself. This one closes
     * a hole: self-service asks for the current password and this path does not, so an administrator
     * pointing it at themselves would be a way to replace their own credential without proving they
     * know it — which is exactly what somebody who has walked up to an unlocked screen wants.
     */
    @Transactional
    public IdentityUser setPassword(String actingAdminId, String targetUserId, String newPassword) {
        requireNotSelf(actingAdminId, targetUserId);

        IdentityUser identityUser = requireById(targetUserId);

        requireItHasAPasswordToSet(identityUser);

        identityUser.setHashedPassword(passwordEncoder.encode(newPassword));
        identityUser.setMustChangePassword(true);

        return identityUserRepository.save(identityUser);
    }

    /**
     * ⚠️ <strong>{@code LOCAL} only — and on a federated account this was not a no-op, it was a
     * lockout.</strong>
     *
     * <p>A {@code GOOGLE} or {@code GITHUB} row has no password by construction: sign-in is the provider's
     * job, and {@code IdentityUserDetailsService} looks an email up scoped to {@code LOCAL}, so a hash
     * written onto one of those rows is a credential no login form will ever check.
     *
     * <p>That much would merely be useless. What made it harmful is the flag that goes with it:
     * {@code mustChangePassword} is enforced by {@code PasswordChangeRequiredFilter} across
     * <em>everything</em> under {@code /api}, {@code /oauth2} and {@code /connect} — so the next time that
     * person signs in through their provider, every product they reach is refused until they clear it. And
     * the only path that clears it is {@code AccountService.changePassword}, which asks for the
     * <em>current</em> password. They never had one. A working federated account is taken out of service by
     * a control whose whole purpose is to put an account back into it.
     *
     * <p>So it is refused where it can be explained, rather than left to be discovered. This is also why
     * the register hides the control on a federated row instead of merely disabling it: the reason belongs
     * next to the account, not in a toast after the fact.
     */
    private void requireItHasAPasswordToSet(IdentityUser identityUser) {
        if (identityUser.getProvider() == Provider.LOCAL) {
            return;
        }

        throw new BusinessRuleViolationException(
            "%s signs in through %s and has no password to set. Setting one would write a credential"
                .formatted(identityUser.getEmail(), identityUser.getProvider())
            + " nothing checks, and would then require a password change they cannot make.");
    }

    @Transactional
    public IdentityUser setEnabled(String actingAdminId, String targetUserId, boolean enabled) {
        requireNotSelf(actingAdminId, targetUserId);
        IdentityUser identityUser = requireById(targetUserId);
        identityUser.setEnabled(enabled);
        return identityUserRepository.save(identityUser);
    }

    /**
     * Removes an account and everything it was granted.
     *
     * <h2>⚠️ The revoke is not tidiness — it is the half nothing else does (ID-12)</h2>
     *
     * <p>{@code access_role_assignments} and {@code access_subject_permissions} are created and mapped by
     * {@code jmouse-access-jpa}. A library table cannot foreign-key into this product's
     * {@code identity_users}, so <strong>nothing cascades and nothing ever will</strong>: without this
     * line the rows simply stay, and they stay silently — no error, no orphan check, just a disclosure
     * screen that grows a row reading "an account that no longer exists" every time somebody is removed.
     *
     * <p>⚠️ <strong>It runs before the delete, in one transaction.</strong> Before, because the revoke is
     * the part that can fail and a half-done removal must be the one that leaves the account intact
     * rather than the one that leaves grants pointing nowhere. In one transaction, because two calls
     * from a controller cannot promise that.
     *
     * <p>The count is returned so the caller can say what went with it — an administrator deleting an
     * account is entitled to know it also took away three roles.
     */
    @Transactional
    public int deleteUser(String actingAdminId, String targetUserId) {
        requireNotSelf(actingAdminId, targetUserId);

        IdentityUser identityUser = requireById(targetUserId);
        int          revoked      = accessAdministrationService.revokeEverythingFor(targetUserId);

        identityUserRepository.delete(identityUser);

        return revoked;
    }

    private IdentityUser requireById(String id) {
        return identityUserRepository.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("No user found for id: " + id));
    }

    private void requireNotSelf(String actingAdminId, String targetUserId) {
        if (actingAdminId.equals(targetUserId)) {
            throw new SelfModificationException();
        }
    }

}
