package net.innoventa.identity.repository;

import net.innoventa.identity.domain.McpCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpCredentialRepository extends JpaRepository<McpCredential, String> {

    /** ⚠️ By hash, because the clear refresh token is never stored — see the entity. */
    Optional<McpCredential> findByRefreshTokenHash(String refreshTokenHash);

    /** What the account page lists: newest first, revoked ones included so they can be read about. */
    List<McpCredential> findBySubjectIdOrderByCreatedAtDesc(String subjectId);
}
