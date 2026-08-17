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

    /**
     * The single, meaningless authority every authenticated row carries.
     *
     * <p>Public because {@code OAuth2LoginSuccessHandler} builds the same session token for a
     * Google/GitHub sign-in, and two spellings of "the authority that decides nothing" is how one of
     * them ends up deciding something.
     */
    public static final String SIGNED_IN = "ROLE_USER";

    private final IdentityUserRepository identityUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        IdentityUser identityUser = identityUserRepository.findByEmailAndProvider(email, Provider.LOCAL)
            .filter(IdentityUser::isEnabled)
            .orElseThrow(() -> new UsernameNotFoundException("No enabled local user found for email: " + email));

        return User.withUsername(identityUser.getId())
            .password(identityUser.getHashedPassword())
            // ⚠️ ONE AUTHORITY FOR EVERYBODY, AND IT DECIDES NOTHING. It used to be
            // "ROLE_" + role.name(), which is what the `/api/admin/**` matcher read; both are gone.
            // What a person may do is resolved by the access engine from `access_*` rows, so an
            // authority here would be a second answer to the same question — and the one Spring
            // Security would quietly prefer if anybody wrote a hasRole(...) again.
            //
            // It is not empty, because a UserDetails with no authorities is a shape several Spring
            // Security components treat as suspicious; it is deliberately meaningless instead.
            .authorities(SIGNED_IN)
            .build();
    }

}
