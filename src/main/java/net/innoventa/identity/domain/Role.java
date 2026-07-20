package net.innoventa.identity.domain;

/**
 * Coarse authorization role for {@link IdentityUser} — deliberately just two values. Per-application
 * permissions and workspace/persona scoping stay local to Innoventa/Moneta; this only
 * gates Identity's own admin user-management panel.
 */
public enum Role {
    USER, ADMIN
}
