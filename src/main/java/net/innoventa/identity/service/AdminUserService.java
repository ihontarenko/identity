package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

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

    public List<IdentityUser> listUsers() {
        return identityUserRepository.findAll().stream()
            .sorted(Comparator.comparing(IdentityUser::getCreatedAt).reversed())
            .toList();
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
