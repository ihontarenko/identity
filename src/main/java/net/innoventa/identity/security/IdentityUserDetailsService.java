package net.innoventa.identity.security;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityUserDetailsService implements UserDetailsService {

    private final IdentityUserRepository identityUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        IdentityUser identityUser = identityUserRepository.findByEmail(email)
            .filter(IdentityUser::isEnabled)
            .orElseThrow(() -> new UsernameNotFoundException("No enabled user found for email: " + email));

        return User.withUsername(identityUser.getEmail())
            .password(identityUser.getHashedPassword())
            .authorities("ROLE_USER")
            .build();
    }

}
