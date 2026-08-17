package net.innoventa.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import net.innoventa.identity.security.oauth2.Provider;

import java.time.LocalDateTime;

@Entity
@Table(name = "identity_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class IdentityUser {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    @Column(nullable = false, length = 128)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "hashed_password", length = 256)
    private String hashedPassword;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * ⚠️ Whether this account is holding a password somebody else chose for it.
     *
     * <p>Set when an administrator raises the account or sets its password, cleared the moment the
     * person changes it themselves. While it stands, the session reaches the change-password call and
     * nothing else — {@code PasswordChangeRequiredFilter} is what enforces that, at the session rather
     * than by hiding a screen, because a credential an administrator chose must not open the other
     * products.
     */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    // ⚠️ THE `role` COLUMN IS STILL IN THE DATABASE AND IS NO LONGER MAPPED. What somebody may do is
    // a row in `access_*` now, resolved by the access engine — see `security/access/Permissions`.
    // The column is left in place on purpose: `AdminRoleHandover` reads it once, after the policy has
    // been seeded, to turn every old ADMIN into real grants. Dropping it in the same change was
    // impossible, and the reason is worth knowing — a Flyway migration runs BEFORE the seed, so at
    // migration time the roles it would have to grant do not exist yet. Expand now, contract in a
    // later release once every installation has run the handover.
    //
    // Hibernate validates only what it is given, so an unmapped extra column is invisible to
    // `ddl-auto: validate`. It carries a NOT NULL DEFAULT 'USER', so inserts are unaffected.

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
