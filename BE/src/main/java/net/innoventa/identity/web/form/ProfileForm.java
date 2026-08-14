package net.innoventa.identity.web.form;

import jakarta.validation.constraints.NotBlank;

public record ProfileForm(@NotBlank String displayName) {
}
