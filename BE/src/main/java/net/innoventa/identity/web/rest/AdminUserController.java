package net.innoventa.identity.web.rest;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.security.access.Scopes;
import net.innoventa.identity.service.AdminUserService;
import net.innoventa.identity.web.rest.dto.AdminUserResponse;
import net.innoventa.identity.web.rest.dto.UpdateEnabledRequest;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ⚠️ <strong>Every method here is now its own enforcement point, and that is the change.</strong>
 *
 * <p>This class used to say the opposite: gated to {@code ROLE_ADMIN} by
 * {@code SecurityConfiguration}'s {@code /api/admin/**} matcher, "not by anything in this class". That
 * matcher is gone, so <strong>a method with no {@code @RequiresAccess} on it is an open endpoint</strong>
 * — which is why the annotations below are exhaustive rather than representative, and why a new method
 * added here without one is a hole rather than an oversight.
 *
 * <p>The class-level declaration supplies the scope and nothing else; every method names the permission
 * it actually needs. Four different ones, where there used to be a single boolean:
 *
 * <ul>
 *   <li>Reading the register discloses every account and its email — {@link Permissions#READ_USER}.
 *   <li>Blocking an account is reversible — {@link Permissions#DISABLE_USER}.
 *   <li>Deleting one is not, and is carried by no role at all — {@link Permissions#DELETE_USER}.
 * </ul>
 *
 * <p>⚠️ {@code requireNotSelf} in {@link AdminUserService} is <strong>not</strong> part of this and
 * stays where it is. It is not "you may not do this to that person" — it is a guard against locking the
 * installation out of itself, it holds for every holder of the permission, and there is no grant that
 * should switch it off.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@RequiresAccess(scope = Scopes.GLOBAL)
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @RequiresAccess(permission = Permissions.READ_USER, scope = Scopes.GLOBAL)
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers().stream().map(AdminUserResponse::from).toList();
    }

    @PatchMapping("/{id}/enabled")
    @RequiresAccess(permission = Permissions.DISABLE_USER, scope = Scopes.GLOBAL)
    public AdminUserResponse setEnabled(
        Authentication authentication,
        @PathVariable String id,
        @RequestBody UpdateEnabledRequest updateEnabledRequest
    ) {
        return AdminUserResponse.from(
            adminUserService.setEnabled(authentication.getName(), id, updateEnabledRequest.enabled()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAccess(permission = Permissions.DELETE_USER, scope = Scopes.GLOBAL)
    public void deleteUser(Authentication authentication, @PathVariable String id) {
        adminUserService.deleteUser(authentication.getName(), id);
    }

}
