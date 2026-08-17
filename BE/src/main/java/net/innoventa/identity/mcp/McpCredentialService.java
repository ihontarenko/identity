package net.innoventa.identity.mcp;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.domain.McpCredential;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.repository.McpCredentialRepository;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;

/**
 * What a protocol credential is in Identity: signed, confined, and revocable.
 *
 * <h2>⚠️ Signed with a secret only this service holds — which looks backwards and is the point</h2>
 *
 * <p>Identity is the authorization server. Every other token it issues is RS256 and verifiable by every
 * product through JWKS, which is exactly what a protocol credential must <strong>not</strong> be: one
 * that reached Innoventa or Tessera would be indistinguishable from a token minted for them. So this one
 * is HS256 over a secret nobody else has, and its confinement is a signature that does not verify rather
 * than a check somebody has to remember to write.
 *
 * <p>It also carries an {@code aud} of its own, deliberately not any of the audiences under
 * {@code identity.clients}. Two independent reasons a credential is refused elsewhere is one more than
 * strictly needed, and the cheap one is the one that survives a refactor.
 *
 * <h2>⚠️ Self-contained and still revocable</h2>
 *
 * <p>The token carries {@link #CONNECTION_CLAIM}, and {@link #admit} looks that row up on every call —
 * so ending a connection takes effect on the next request rather than when a month-old token finally
 * expires. Without it, "disconnect" would be a button that does nothing for four weeks.
 */
@Service
@RequiredArgsConstructor
public class McpCredentialService {

    /** Names the connection. ⚠️ Its absence means the token was not minted for this endpoint. */
    public static final String CONNECTION_CLAIM = "cid";

    /**
     * How long between two "last used" writes for the same connection.
     *
     * <p>⚠️ Every protocol call passes through the validator, and a busy client would otherwise turn
     * each tool call into an update. The column is read by a person on a screen; to the minute is
     * plenty.
     */
    private static final Duration USAGE_WRITE_INTERVAL = Duration.ofMinutes(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final McpCredentialRepository  credentials;
    private final IdentityUserRepository   accounts;
    private final JwtEncoder               mcpJwtEncoder;
    private final McpAuthorizationSettings settings;

    /**
     * Mints for a person, against a client that has just been approved.
     *
     * <p>⚠️ <strong>One row per connection, never per person.</strong> Somebody may attach a second
     * client without the first losing its credential, and ending one must not end the other.
     */
    @Transactional
    public IssuedCredential issueFor(String subjectId, String clientName, String clientId) {
        String refreshToken = randomToken();

        McpCredential credential = credentials.save(McpCredential.builder()
            .id(UUID.randomUUID().toString())
            .subjectId(subjectId)
            .clientId(clientId)
            .clientName(clientName)
            .refreshTokenHash(hash(refreshToken))
            .createdAt(LocalDateTime.now())
            .build());

        return new IssuedCredential(
            accessTokenFor(credential), refreshToken, settings.accessTokenLifetime().toSeconds());
    }

    /**
     * Renews against a refresh token a client already holds.
     *
     * <p>⚠️ <strong>The refresh token is not rotated.</strong> Rotation would be better and is a
     * decision with a failure mode of its own — a client that loses the response loses the connection —
     * so it is left as it is rather than half-done. The connection is revocable either way, which is
     * the property that actually matters here.
     */
    @Transactional
    public IssuedCredential renew(String refreshToken) {
        McpCredential credential = credentials.findByRefreshTokenHash(hash(refreshToken))
            .orElseThrow(() -> new IllegalArgumentException("This refresh token is not one of ours."));

        if (credential.isRevoked()) {
            throw new IllegalArgumentException("This connection has been ended.");
        }

        if (expired(credential)) {
            throw new IllegalArgumentException(
                "This connection has not been used inside its renewal window and has to be authorized "
                + "again.");
        }

        return new IssuedCredential(
            accessTokenFor(credential), refreshToken, settings.accessTokenLifetime().toSeconds());
    }

    /**
     * Why this credential should be refused, or empty where it should not.
     *
     * <p>Asked on <strong>every</strong> protocol call. Three things can have changed since it was
     * minted, and each is a different sentence: the connection was ended, the account was disabled, or
     * the account is gone.
     */
    @Transactional(readOnly = true)
    public Optional<String> admit(String connectionId) {
        Optional<McpCredential> found = credentials.findById(connectionId);

        if (found.isEmpty()) {
            return Optional.of("This connection is not one this installation knows about.");
        }

        McpCredential credential = found.get();

        if (credential.isRevoked()) {
            return Optional.of("This connection has been ended from the account it was approved on.");
        }

        Optional<IdentityUser> account = accounts.findById(credential.getSubjectId());

        if (account.isEmpty()) {
            return Optional.of("The account this connection acts as no longer exists.");
        }

        if (!account.get().isEnabled()) {
            return Optional.of("The account this connection acts as has been blocked.");
        }

        // ⚠️ A credential must not outlive the forced password change it was minted around: an account
        // holding an administrator-chosen password reaches nothing through the browser, and a protocol
        // client is not the way round that.
        if (account.get().isMustChangePassword()) {
            return Optional.of(
                "The account this connection acts as has to change its password before doing anything.");
        }

        return Optional.empty();
    }

    /** Stamps use, at most once per {@link #USAGE_WRITE_INTERVAL} for one connection. */
    @Transactional
    public void noteUsage(String connectionId) {
        credentials.findById(connectionId).ifPresent(credential -> {
            LocalDateTime now = LocalDateTime.now();

            if (credential.getLastUsedAt() != null
                && credential.getLastUsedAt().isAfter(now.minus(USAGE_WRITE_INTERVAL))) {
                return;
            }

            credential.setLastUsedAt(now);
            credentials.save(credential);
        });
    }

    /** What the account page lists — every client this person has ever connected, newest first. */
    @Transactional(readOnly = true)
    public List<McpCredential> connectionsOf(String subjectId) {
        return credentials.findBySubjectIdOrderByCreatedAtDesc(subjectId);
    }

    /**
     * Ends one connection.
     *
     * <p>⚠️ Only the person it belongs to, checked here rather than by the caller. A connection is not
     * an administrative object: it is somebody's own key, and nobody else's list to edit.
     */
    @Transactional
    public boolean revoke(String subjectId, String connectionId) {
        return credentials.findById(connectionId)
            .filter(credential -> credential.getSubjectId().equals(subjectId))
            .filter(credential -> !credential.isRevoked())
            .map(credential -> {
                credential.setRevokedAt(LocalDateTime.now());
                credentials.save(credential);

                return true;
            })
            .orElse(false);
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private String accessTokenFor(McpCredential credential) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(settings.resourceUrl())
            .audience(List.of(settings.audience()))
            .subject(credential.getSubjectId())
            .claim(CONNECTION_CLAIM, credential.getId())
            .issuedAt(now)
            .expiresAt(now.plus(settings.accessTokenLifetime()))
            .build();

        return mcpJwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(HS256).build(), claims))
            .getTokenValue();
    }

    private boolean expired(McpCredential credential) {
        return credential.getCreatedAt()
            .plus(settings.refreshTokenLifetime())
            .isBefore(LocalDateTime.now());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];

        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * ⚠️ Plain SHA-256 rather than a password hash, and that is correct here: the input is 256 random
     * bits rather than something somebody chose, so there is nothing to brute-force and a deliberately
     * slow hash would only be slow. It is a lookup key that happens to be one-way.
     */
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", impossible);
        }
    }

    /** A credential, in the three parts the token response is built from. */
    public record IssuedCredential(String accessToken, String refreshToken, long expiresInSeconds) {}
}
