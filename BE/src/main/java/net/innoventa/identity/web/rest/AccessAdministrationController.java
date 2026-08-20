package net.innoventa.identity.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.security.access.Scopes;
import net.innoventa.identity.service.AccessAdministrationService;
import net.innoventa.identity.service.access.PolicyProjectionService;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.AccessOverview;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.AssignRoleRequest;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.GrantPermissionRequest;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.RevokePermissionRequest;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.RoleView;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.SetBundleRequest;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The installation's access screen: what the roles carry, who holds them, and what one person holds
 * personally.
 *
 * <p>Every route is gated on {@link Permissions#ADMINISTER_ACCESS} at the class level — it discloses
 * every grant in the installation, and it hands them out.
 *
 * <p>⚠️ <strong>It is deliberately not also gated on {@code user:read}</strong>, even though it lists
 * people. The screen needs the register, so {@code GLOBAL_ACCESS_ADMINISTRATOR} carries
 * {@code user:read} in the policy instead. One gate, one refusal, one sentence explaining it.
 */
@RestController
@RequestMapping("/api/admin/access")
@RequiredArgsConstructor
@RequiresAccess(permission = Permissions.ADMINISTER_ACCESS, scope = Scopes.GLOBAL)
public class AccessAdministrationController {

    private final AccessAdministrationService accessAdministrationService;
    private final PolicyProjectionService     policyProjectionService;

    /** Everything the screen shows, in one request — the vocabulary, the roles, and every holding. */
    @GetMapping
    public AccessOverview overview() {
        return accessAdministrationService.overview();
    }

    /**
     * The same authorization, rendered back into the policy language — read-only.
     *
     * <p>⚠️ <strong>Text, not JSON.</strong> The value of this tab is that it reads like the file somebody
     * already knows how to read; a structured payload the client re-renders would be a second implementation
     * of the grammar, and the two would drift.
     */
    @GetMapping(value = "/projection", produces = "text/plain;charset=UTF-8")
    public String projection() {
        return policyProjectionService.render();
    }

    /**
     * What a role carries from now on.
     *
     * <p>⚠️ The whole bundle, never a difference — see the service for why. Addressed by name, because
     * a role's name is what the engine stores and what the policy document writes; a surrogate
     * identifier here would be a second way to say the same thing.
     */
    @PutMapping("/roles/{roleName}/bundle")
    public RoleView setBundle(@PathVariable String roleName, @Valid @RequestBody SetBundleRequest request) {
        return accessAdministrationService.setBundle(roleName, request.bundle());
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(Authentication authentication, @Valid @RequestBody AssignRoleRequest request) {
        accessAdministrationService.assign(
            request.accountId(), request.roleName(), authentication.getName());
    }

    @DeleteMapping("/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@Valid @RequestBody AssignRoleRequest request) {
        accessAdministrationService.unassign(request.accountId(), request.roleName());
    }

    /**
     * ⚠️ <strong>A deny beats every role that grants it.</strong> This is the sharpest thing on the
     * screen: it is how one person loses one power without the role that gives it to everybody else
     * being touched — and it is why the service refuses one without a reason.
     *
     * <p>It is also the only way {@link Permissions#DELETE_USER} is ever held, no role carrying it.
     */
    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(Authentication authentication, @Valid @RequestBody GrantPermissionRequest request) {
        accessAdministrationService.grant(
            request.accountId(), request.permission(), request.allowed(), request.reason(),
            authentication.getName());
    }

    @DeleteMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ungrant(@Valid @RequestBody RevokePermissionRequest request) {
        accessAdministrationService.ungrant(request.accountId(), request.permission());
    }
}
