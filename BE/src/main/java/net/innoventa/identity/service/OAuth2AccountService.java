package net.innoventa.identity.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Mirrors Innoventa's {@code AuthenticationService.upsertOAuth2User} — same lookup-by-(email,
 * provider) shape, same "existing row just gets its display name refreshed, new row gets created
 * enabled with no password" behavior. Kept separate from {@link AccountService} deliberately:
 * that service is self-service operations for an already-authenticated user; this one is
 * pre-authentication identity resolution called from {@code OAuth2LoginSuccessHandler}, a
 * different concern with no HTTP/session context of its own yet.
 */
@Service
@RequiredArgsConstructor
public class OAuth2AccountService {

    private final IdentityUserRepository identityUserRepository;

    @Transactional
    public IdentityUser upsertOAuth2User(String email, String displayName, Provider provider) {
        return identityUserRepository.findByEmailAndProvider(email, provider)
            .map(existing -> {
                existing.setDisplayName(displayName);
                return identityUserRepository.save(existing);
            })
            .orElseGet(() -> identityUserRepository.save(IdentityUser.builder()
                .id(UUID.randomUUID().toString())
                .provider(provider)
                .email(email)
                .displayName(displayName)
                .enabled(true)
                .build()));
    }

}
