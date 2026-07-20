package net.innoventa.identity.web.rest.dto;

import net.innoventa.identity.domain.IdentityUser;

import java.time.LocalDateTime;

public record AdminUserResponse(
    String id, String email, String displayName, String provider,
    String role, boolean enabled, LocalDateTime createdAt
) {

    public static AdminUserResponse from(IdentityUser identityUser) {
        return new AdminUserResponse(
            identityUser.getId(),
            identityUser.getEmail(),
            identityUser.getDisplayName(),
            identityUser.getProvider().name(),
            identityUser.getRole().name(),
            identityUser.isEnabled(),
            identityUser.getCreatedAt()
        );
    }

}
