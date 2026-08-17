package net.innoventa.identity.web.rest.dto;

import net.innoventa.identity.domain.IdentityUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One account, as the register lists it.
 *
 * <p>⚠️ <strong>{@code role} is gone and is not replaced here.</strong> It carried {@code "USER"} or
 * {@code "ADMIN"} and the register rendered it as a badge, which stopped meaning anything the moment a
 * person could hold {@code user:delete} personally without holding a role at all.
 *
 * <p>Everything <em>finer</em> than a role — personal allows and denies, what each role carries, where
 * a holding came from — remains a question for the access screen (ID-5). Answering all of it here
 * would be a second, worse copy of that screen.
 */
public record AdminUserResponse(
    String id, String email, String displayName, String provider,
    boolean enabled,
    /** Whether the account has yet to replace the password an administrator set for it. */
    boolean mustChangePassword,
    /**
     * ⚠️ <strong>Empty unless the caller holds {@code access:administer}</strong>, and never merely
     * because the account holds nothing. Reading the register is one power and seeing who holds what
     * is another; filling this in for anybody with {@code user:read} would widen that permission to
     * include everybody's grants, through a column somebody added for convenience.
     *
     * <p>The interface renders the column only when it is populated, so an unentitled reader sees the
     * register without it rather than seeing an empty one and concluding nobody holds anything.
     */
    List<String> roles,
    LocalDateTime createdAt
) {

    public static AdminUserResponse from(IdentityUser identityUser, List<String> roles) {
        return new AdminUserResponse(
            identityUser.getId(),
            identityUser.getEmail(),
            identityUser.getDisplayName(),
            identityUser.getProvider().name(),
            identityUser.isEnabled(),
            identityUser.isMustChangePassword(),
            roles == null ? List.of() : roles,
            identityUser.getCreatedAt()
        );
    }

    /** For the routes that answer about one account and say nothing about holdings. */
    public static AdminUserResponse from(IdentityUser identityUser) {
        return from(identityUser, List.of());
    }

}
