package net.innoventa.identity.web.rest.dto;

import net.innoventa.identity.domain.IdentityUser;

import java.time.LocalDateTime;

/**
 * One account, as the register lists it.
 *
 * <p>⚠️ <strong>{@code role} is gone and is not replaced here.</strong> It carried {@code "USER"} or
 * {@code "ADMIN"} and the register rendered it as a badge, which stopped meaning anything the moment a
 * person could hold {@code user:delete} personally without holding a role at all.
 *
 * <p>What somebody holds is a question for the access screen (ID-5), which reads roles, holders and
 * personal overrides together. Answering half of it in a badge on the users list would be the same
 * mistake in smaller type — and it would need this endpoint to disclose everybody's grants to anybody
 * who may merely read the register.
 */
public record AdminUserResponse(
    String id, String email, String displayName, String provider,
    boolean enabled,
    /** Whether the account has yet to replace the password an administrator set for it. */
    boolean mustChangePassword,
    LocalDateTime createdAt
) {

    public static AdminUserResponse from(IdentityUser identityUser) {
        return new AdminUserResponse(
            identityUser.getId(),
            identityUser.getEmail(),
            identityUser.getDisplayName(),
            identityUser.getProvider().name(),
            identityUser.isEnabled(),
            identityUser.isMustChangePassword(),
            identityUser.getCreatedAt()
        );
    }

}
