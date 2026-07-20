package net.innoventa.identity.security.oauth2;

/**
 * Mirrors Innoventa's {@code net.innoventa.security.oauth2.Provider} — same values, same meaning.
 * A user's identity row is scoped to exactly one of these; the same email can have separate
 * {@code LOCAL}/{@code GOOGLE}/{@code GITHUB} rows (see {@code identity_users_unique_email_provider}
 * in {@code V000001__identity_users.sql}).
 */
public enum Provider {
    LOCAL, GOOGLE, GITHUB
}
