package net.innoventa.identity.web.rest.dto;

import net.innoventa.identity.domain.IdentityUser;

import java.util.List;
import java.util.Set;

/**
 * Who the caller is, and what the caller may do.
 *
 * <p>⚠️ <strong>{@code permissions} replaced {@code role}, and it is not a rename.</strong> The old
 * field carried {@code "USER"} or {@code "ADMIN"} and the interface branched on it, so every control
 * on every administrative screen was rendered from one boolean. A screen that branches on a role name
 * breaks the moment somebody is granted a permission personally without the bundle — which is exactly
 * how {@code user:delete} is held, since it sits in no role at all.
 *
 * <p>So what leaves here is the flat set of permission names, and a control is rendered from the
 * permission that backs it. It is the caller's own set and nobody else's.
 */
public record MeResponse(
    String id, String email, String displayName, String avatarUrl, String provider,
    List<String> permissions,
    /**
     * ⚠️ Whether this account is holding a password an administrator chose for it. The interface uses
     * it to render the change-password screen and nothing else — the <em>enforcement</em> is
     * {@code PasswordChangeRequiredFilter}, at the session, because a gate the client applies is a
     * gate a client can decline to apply.
     */
    boolean mustChangePassword
) {

    public static MeResponse from(IdentityUser identityUser, Set<String> permissions) {
        return new MeResponse(
            identityUser.getId(),
            identityUser.getEmail(),
            identityUser.getDisplayName(),
            identityUser.getAvatarUrl(),
            identityUser.getProvider().name(),
            List.copyOf(permissions),
            identityUser.isMustChangePassword()
        );
    }

}
