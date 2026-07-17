package net.innoventa.identity.security;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.User;
import net.innoventa.identity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .filter(User::isEnabled)
            .orElseThrow(() -> new UsernameNotFoundException("No enabled user found for email: " + email));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getHashedPassword())
            .authorities("ROLE_USER")
            .build();
    }

}
