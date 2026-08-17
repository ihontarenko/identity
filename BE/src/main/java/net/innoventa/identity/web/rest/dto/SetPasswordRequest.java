package net.innoventa.identity.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An administrator setting somebody else's password — the recovery path until a real reset flow
 * exists.
 *
 * <p>⚠️ No current password, because the administrator does not know it and that is the point. Which
 * is exactly why this asks for {@code user:password} rather than inheriting from editing a profile,
 * and why the account is made to replace what is set here.
 */
public record SetPasswordRequest(
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
) {
}
