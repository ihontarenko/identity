package net.innoventa.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One client connected to the protocol endpoint.
 *
 * <p>The row exists so that a self-contained token is revocable. Its {@link #id} travels in the token's
 * {@code cid} claim and is looked up on every protocol call, which is the difference between ending a
 * connection now and waiting a month for it to expire.
 *
 * <p>⚠️ <strong>The refresh token is stored as a hash and never in the clear.</strong> It is a
 * long-lived credential in a table an operator can read; the only thing that ever needs to match it is
 * a renewal request presenting it, and a hash answers that without the table being worth stealing.
 */
@Entity
@Table(name = "mcp_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class McpCredential {

    /** ⚠️ Also the token's {@code cid} claim — see the class note. */
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    /** The {@code identity_users} row this credential acts as. Never an account of its own. */
    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;

    /** ⚠️ Issued by the registry rather than claimed by the client — the half worth keying on. */
    @Column(name = "client_id", length = 128)
    private String clientId;

    /** ⚠️ What the client called itself. A claim, for a screen and a log line, never an identity. */
    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "refresh_token_hash", length = 128, nullable = false)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Stamped on use, rate-limited — so the account page can say when a client was last seen. */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** Set rather than deleted, so a connection somebody ended remains something they can read about. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
