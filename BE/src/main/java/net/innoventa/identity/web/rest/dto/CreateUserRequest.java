package net.innoventa.identity.web.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Raise an account.
 *
 * <p>⚠️ <strong>There is no {@code provider} here.</strong> A Google or GitHub row is created by that
 * provider's first sign-in and cannot honestly be typed in by hand — see {@code AdminUserService}.
 *
 * <p>⚠️ <strong>The password rule is the same one self-service uses</strong> ({@code PasswordForm}),
 * deliberately: a weaker rule for the password an administrator picks would put the weakest credential
 * in the installation on the account that has not been used yet.
 */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 128) String email,
    @Size(max = 255) String displayName,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String initialPassword
) {
}
