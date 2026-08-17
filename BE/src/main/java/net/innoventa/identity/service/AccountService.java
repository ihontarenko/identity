package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately free of any HTTP/view concerns (no Model, no BindingResult) — this is the layer a
 * future REST API (for an app like Innoventa, which will want to keep its own profile page UI and
 * call Identity server-to-server once it migrates onto centralized identity) can sit on top of
 * without any rework here.
 *
 * <p>Keyed by the user's row {@code id}, not email — the authenticated principal's name is always
 * the id (see {@code IdentityUserDetailsService}), and email alone is ambiguous once the same
 * address has separate LOCAL/GOOGLE/GITHUB rows.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final IdentityUserRepository identityUserRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityUser requireById(String id) {
        return identityUserRepository.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("No user found for id: " + id));
    }

    @Transactional
    public void updateDisplayName(String id, String displayName) {
        IdentityUser identityUser = requireById(id);
        identityUser.setDisplayName(displayName);
        identityUserRepository.save(identityUser);
    }

    @Transactional
    public void updateAvatarUrl(String id, String avatarUrl) {
        IdentityUser identityUser = requireById(id);
        identityUser.setAvatarUrl(avatarUrl);
        identityUserRepository.save(identityUser);
    }

    /**
     * ⚠️ <strong>This is the one path that clears {@code mustChangePassword}, and it has to stay the
     * one.</strong> An administrator can set somebody's password (see {@code AdminUserService}), which
     * <em>sets</em> the flag; only the person themselves changing it clears it. A second
     * clearing path is how one of the two ends up not clearing it, and a flag that is sometimes not
     * cleared locks somebody out of every product with no way back.
     */
    @Transactional
    public void changePassword(String id, String currentPassword, String newPassword) {
        IdentityUser identityUser = requireById(id);
        if (identityUser.getHashedPassword() == null
            || !passwordEncoder.matches(currentPassword, identityUser.getHashedPassword())) {
            throw new InvalidCurrentPasswordException();
        }
        identityUser.setHashedPassword(passwordEncoder.encode(newPassword));
        identityUser.setMustChangePassword(false);
        identityUserRepository.save(identityUser);
    }

}
