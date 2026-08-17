package net.innoventa.identity.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.CallerPermissions;
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
 *
 * <h2>⚠️ Deliberately not gated by the access engine, and this is a deviation worth reading</h2>
 *
 * <p>The ticket's endpoint table put {@code @SELF} on these routes. Written literally that would mean
 * every person needs a {@code user:edit @SELF} grant before they can change their own display name —
 * and <strong>nothing in this service assigns anybody anything on sign-in</strong>: there is no
 * provisioner, no default role, and the policy document grants only the bootstrap owner. Every account
 * but one would arrive holding nothing and be unable to manage itself, which is a lockout dressed as a
 * permission model.
 *
 * <p>The honest reading is that self-service is not an authorization question here. Your own account is
 * yours by construction: {@code authentication.getName()} <em>is</em> the row id, so these methods
 * cannot act on anybody else no matter what is granted. Being signed in is the whole requirement, and
 * the filter chain already says so.
 *
 * <p>{@link net.innoventa.identity.security.access.IdentityScope#SELF} therefore stays registered and
 * unused for now — the day a permission genuinely differs between one's own rows and everybody's, it is
 * already there.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountRestController {

    private final AccountService     accountService;
    private final CallerPermissions  callerPermissions;

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return current(authentication);
    }

    @PutMapping("/account/profile")
    public MeResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileForm profileForm) {
        accountService.updateDisplayName(authentication.getName(), profileForm.displayName());
        return current(authentication);
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

    /**
     * The caller, with the permissions they hold right now.
     *
     * <p>Resolved on the way out of a write as well as on a plain read, so that a screen re-rendered
     * after an edit never carries a stale set — a grant taken away between two requests must not go on
     * showing a control the next call would refuse.
     */
    private MeResponse current(Authentication authentication) {
        return MeResponse.from(
            accountService.requireById(authentication.getName()), callerPermissions.ofCaller());
    }

}
