package net.innoventa.identity.repository;

import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, String> {

    Optional<IdentityUser> findByEmailAndProvider(String email, Provider provider);

}
