package net.innoventa.identity.web.rest.dto;

import net.innoventa.identity.domain.IdentityUser;

public record MeResponse(
    String id, String email, String displayName, String avatarUrl, String provider, String role
) {

    public static MeResponse from(IdentityUser identityUser) {
        return new MeResponse(
            identityUser.getId(),
            identityUser.getEmail(),
            identityUser.getDisplayName(),
            identityUser.getAvatarUrl(),
            identityUser.getProvider().name(),
            identityUser.getRole().name()
        );
    }

}
