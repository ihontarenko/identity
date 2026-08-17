package net.innoventa.identity.mcp.tool;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.service.AccessAdministrationService;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.AccessOverview;
import net.innoventa.identity.web.rest.dto.AccessAdministrationDtos.RoleHoldingView;
import org.jmouse.ai.AffectedRecords;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Who holds what, and handing it out.
 *
 * <p>This is the half that makes <em>"add this person, and let them raise accounts"</em> one sentence
 * rather than a screen. Everything it does is what the access screen does, through the same service,
 * validated against the same catalogues.
 *
 * <p>⚠️ <strong>Roles only — no personal allow or deny.</strong> {@code user:delete} is carried by no
 * role precisely so that holding it is a deliberate, visible act by a named person; a tool that could
 * grant it would make a sentence the shortest path to the most dangerous permission in the
 * installation. The overrides tab is where that lives, and it stays there.
 */
@Component
@RequiredArgsConstructor
public class AccessTool implements ToolDefinition {

    private final AccessAdministrationService accessAdministrationService;

    @Override
    public String toolName() {
        return "access";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(read(), grant(), revoke());
    }

    private ToolAction read() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("read")
            .title("Read the installation's access")
            .description("Everything at once: the permissions this build knows, what each role "
                       + "carries, who holds which role, and every personal allow or deny. Read this "
                       + "before granting anything — a role name is never invented, and this is where "
                       + "the ones that exist are listed. ⚠️ It discloses every grant in the "
                       + "installation, which is why it costs a permission of its own.")
            .inputSchema(ArgumentSchema.none())
            .requiredPermission(Permissions.ADMINISTER_ACCESS)
            .readOnly()
            .handler(invocation -> describe(accessAdministrationService.overview()))
            .build();
    }

    /**
     * ⚠️ <strong>Not confirmed.</strong> Granting is reversible by the very next call, and confirming
     * every one would make the tool unusable in a conversation for no safety gained — the same
     * reasoning Tessera applies to a transition.
     */
    private ToolAction grant() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("grant")
            .title("Give somebody a role")
            .description("Grants a role installation-wide. Role names come from access_read and are "
                       + "refused by name with the list that would have worked. Editing what a role "
                       + "CARRIES is deliberately not offered here: that changes it for everybody "
                       + "holding it at once, which is a screen's decision rather than a sentence's.")
            .inputSchema(ArgumentSchema.builder()
                .requiredString("accountId", "The account's id, as users_list reports it.")
                .requiredString("roleName", "A role name, as access_read reports it."))
            .requiredPermission(Permissions.ADMINISTER_ACCESS)
            .handler(this::handleGrant)
            .build();
    }

    private Object handleGrant(ToolInvocation invocation) {
        accessAdministrationService.assign(
            invocation.requiredString("accountId"),
            invocation.requiredString("roleName"),
            invocation.actingSubject());

        return Map.of("granted", true);
    }

    /**
     * ⚠️ <strong>Confirmed, and it is the only action here that is.</strong>
     *
     * <p>Revoking is how somebody gets locked out of the access screen — <em>including the person
     * asking</em>, since nothing stops them taking their own last role away. It also resolves the
     * target by identity, so a grant cannot be removed from a name a model merely believes in: the
     * preview names the account and the role, and the same arguments plus the token carry out exactly
     * what was previewed.
     */
    private ToolAction revoke() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("revoke")
            .title("Take a role back")
            .description("Takes a role back, installation-wide. ⚠️ Previewed first: the answer names "
                       + "the holding that would be removed and returns a token, and resending the "
                       + "identical arguments with it carries out exactly that. Nothing stops this "
                       + "removing the last role from the account asking, so read the preview.")
            .inputSchema(ArgumentSchema.builder()
                .requiredString("accountId", "The account's id, as access_read reports it.")
                .requiredString("roleName", "A role name, as access_read reports it.")
                .confirm())
            .requiredPermission(Permissions.ADMINISTER_ACCESS)
            .destructive()
            .affectedRecords(this::holdingBeingRemoved)
            .handler(this::handleRevoke)
            .build();
    }

    /**
     * What the preview names — resolved from the holdings that actually exist, so a role nobody holds
     * previews as nothing rather than as a plausible removal.
     */
    private AffectedRecords holdingBeingRemoved(ToolInvocation invocation) {
        String accountId = invocation.requiredString("accountId");
        String roleName  = invocation.requiredString("roleName");

        List<AffectedRecords.Record> holdings = accessAdministrationService.overview().roleHoldings()
            .stream()
            .filter(holding -> holding.account() != null && holding.account().id().equals(accountId))
            .filter(holding -> holding.roleName().equals(roleName))
            .map(AccessTool::asRecord)
            .toList();

        return new AffectedRecords(holdings, holdings.size());
    }

    private static AffectedRecords.Record asRecord(RoleHoldingView holding) {
        return AffectedRecords.Record.of(
            holding.account().id(),
            holding.roleName() + " held by " + holding.account().email(),
            "role holding");
    }

    private Object handleRevoke(ToolInvocation invocation) {
        accessAdministrationService.unassign(
            invocation.requiredString("accountId"), invocation.requiredString("roleName"));

        return Map.of("revoked", true);
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private static Map<String, Object> describe(AccessOverview overview) {
        return Map.of(
            "permissions", overview.permissions(),
            "roles", overview.roles(),
            "roleHoldings", overview.roleHoldings(),
            "directHoldings", overview.directHoldings());
    }
}
