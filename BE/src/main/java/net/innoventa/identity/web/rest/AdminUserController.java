package net.innoventa.identity.web.rest;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.service.AdminUserService;
import net.innoventa.identity.web.rest.dto.AdminUserResponse;
import net.innoventa.identity.web.rest.dto.UpdateEnabledRequest;
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
 * Gated to {@code ROLE_ADMIN} by {@code SecurityConfiguration.defaultSecurityFilterChain}'s
 * {@code /api/admin/**} rule, not by anything in this class — a missing role check here would
 * still be blocked at the filter-chain level, but the matcher is the actual enforcement point.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminUserService.listUsers().stream().map(AdminUserResponse::from).toList();
    }

    @PatchMapping("/{id}/enabled")
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
    public void deleteUser(Authentication authentication, @PathVariable String id) {
        adminUserService.deleteUser(authentication.getName(), id);
    }

}
