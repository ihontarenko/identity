package net.innoventa.identity.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.service.AccountService;
import net.innoventa.identity.web.form.PasswordForm;
import net.innoventa.identity.web.form.ProfileForm;
import net.innoventa.identity.web.rest.dto.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The REST layer {@code AccountService}'s own Javadoc names as its reason for staying free of
 * HTTP/view concerns — self-service operations for whichever user the session belongs to
 * ({@code authentication.getName()} is always the row id, see {@code IdentityUserDetailsService}).
 * Replaces {@code web.AccountController} (removed alongside the Thymeleaf templates).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountRestController {

    private final AccountService accountService;

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return MeResponse.from(accountService.requireById(authentication.getName()));
    }

    @PutMapping("/account/profile")
    public MeResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileForm profileForm) {
        accountService.updateDisplayName(authentication.getName(), profileForm.displayName());
        return MeResponse.from(accountService.requireById(authentication.getName()));
    }

    @PutMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordForm passwordForm) {
        if (!passwordForm.newPassword().equals(passwordForm.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        accountService.changePassword(
            authentication.getName(), passwordForm.currentPassword(), passwordForm.newPassword());
    }

}
