package net.innoventa.identity.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordForm(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword,
    @NotBlank String confirmPassword
) {
}
