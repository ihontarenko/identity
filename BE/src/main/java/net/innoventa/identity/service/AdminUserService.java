package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;

/**
 * Backs the admin user-management panel. Kept separate from {@link AccountService} — that one is
 * self-service for whichever user owns the session, this one acts on an arbitrary target user on
 * an admin's behalf ({@code SecurityConfiguration} gates every {@code /api/admin/**} call to
 * {@code ROLE_ADMIN} before it reaches here).
 *
 * <p>Delete is a hard delete — no other table in this database references {@code identity_users}
 * yet (Moneta's {@code workspaces.owner_subject} is a separate database, referenced only by
 * string convention), and there's no audit requirement recorded anywhere that would call for a
 * soft-delete flag instead.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final IdentityUserRepository identityUserRepository;
    private final PasswordEncoder        passwordEncoder;

    public List<IdentityUser> listUsers() {
        return identityUserRepository.findAll().stream()
            .sorted(Comparator.comparing(IdentityUser::getCreatedAt).reversed())
            .toList();
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
    public IdentityUser createUser(String email, String displayName, String initialPassword) {
        String trimmed = email == null ? "" : email.trim();

        identityUserRepository.findByEmailAndProvider(trimmed, Provider.LOCAL).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                "There is already a local account for " + trimmed + ".");
        });

        return identityUserRepository.save(IdentityUser.builder()
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

        identityUser.setHashedPassword(passwordEncoder.encode(newPassword));
        identityUser.setMustChangePassword(true);

        return identityUserRepository.save(identityUser);
    }

    @Transactional
    public IdentityUser setEnabled(String actingAdminId, String targetUserId, boolean enabled) {
        requireNotSelf(actingAdminId, targetUserId);
        IdentityUser identityUser = requireById(targetUserId);
        identityUser.setEnabled(enabled);
        return identityUserRepository.save(identityUser);
    }

    @Transactional
    public void deleteUser(String actingAdminId, String targetUserId) {
        requireNotSelf(actingAdminId, targetUserId);
        IdentityUser identityUser = requireById(targetUserId);
        identityUserRepository.delete(identityUser);
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
