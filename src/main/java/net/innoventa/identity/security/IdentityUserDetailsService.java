package net.innoventa.identity.security;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Form login only ever authenticates {@code LOCAL} rows — a {@code GOOGLE}/{@code GITHUB} row for
 * the same email has no password to check against anyway, and scoping here explicitly (rather than
 * relying on a null-password mismatch) keeps the intent visible.
 *
 * <p>The returned {@link UserDetails#getUsername()} is the row's own {@code id}, not its email —
 * {@code loadUserByUsername}'s parameter is what the user typed (their email), but what becomes the
 * authenticated principal's name (and therefore every issued token's {@code sub} claim, see
 * {@code SecurityConfiguration}) must be unambiguous even once the same email has multiple provider
 * rows. Spring Security doesn't require the two to match; the id-as-username here is deliberate.
 */
@Service
@RequiredArgsConstructor
public class IdentityUserDetailsService implements UserDetailsService {

    private final IdentityUserRepository identityUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        IdentityUser identityUser = identityUserRepository.findByEmailAndProvider(email, Provider.LOCAL)
            .filter(IdentityUser::isEnabled)
            .orElseThrow(() -> new UsernameNotFoundException("No enabled local user found for email: " + email));

        return User.withUsername(identityUser.getId())
            .password(identityUser.getHashedPassword())
            .authorities("ROLE_" + identityUser.getRole().name())
            .build();
    }

}
