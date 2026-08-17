package net.innoventa.identity.web.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Raise an account, optionally with the roles it should hold from the moment it exists.
 *
 * <p>⚠️ <strong>There is no {@code provider} here.</strong> A Google or GitHub row is created by that
 * provider's first sign-in and cannot honestly be typed in by hand — see {@code AdminUserService}.
 *
 * <p>⚠️ <strong>The password rule is the same one self-service uses</strong> ({@code PasswordForm}),
 * deliberately: a weaker rule for the password an administrator picks would put the weakest credential
 * in the installation on the account that has not been used yet.
 *
 * @param roles role names, installation-wide, or empty for an account that holds nothing yet.
 *              ⚠️ <strong>Naming any of them requires {@code access:administer} as well as
 *              {@code user:create}</strong> — they are two separate powers, and one request must not
 *              quietly combine them. There are deliberately no personal overrides here: keeping
 *              {@code user:delete} out of the create form is what stops it becoming the shortest path
 *              to the most dangerous permission in the installation
 */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 128) String email,
    @Size(max = 255) String displayName,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String initialPassword,
    List<String> roles
) {

    /** Never null, so nothing downstream has to ask. */
    public List<String> roles() {
        return roles == null ? List.of() : roles;
    }
}
