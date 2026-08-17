package net.innoventa.identity.mcp.tool;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.security.access.Permissions;
import net.innoventa.identity.service.AdminUserService;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.jmouse.ai.ToolRefusedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The accounts this installation holds — reading them, raising one, blocking one.
 *
 * <h2>⚠️ Two things this deliberately does not offer</h2>
 *
 * <ul>
 *   <li><strong>Deleting an account.</strong> The permission exists and the button exists; the
 *       <em>tool</em> does not. Deletion here is a hard delete with no audit row and no undo, and a
 *       sentence that merely <em>sounded</em> like it wanted an account gone is the likeliest way to
 *       lose one. It stays where a person clicks it. This is a decision about the surface, not about
 *       the right.
 *   <li><strong>Setting somebody's password.</strong> It is impersonation with a delay, and it would
 *       put a live secret in a tool argument, where a transcript keeps it.
 * </ul>
 *
 * <p>Each action declares the permission its equivalent screen control declares, and
 * {@link IdentityToolAuthorizer} resolves it against the same rows. A tool being present says nothing
 * about whether this caller may use it.
 */
@Component
@RequiredArgsConstructor
public class UserTool implements ToolDefinition {

    private final AdminUserService adminUserService;

    @Override
    public String toolName() {
        return "users";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(list(), get(), create(), setEnabled());
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    private ToolAction list() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("list")
            .title("List accounts")
            .description("Lists every account in this installation — its email, display name, which "
                       + "provider it signs in with, whether it is enabled, and whether it still has "
                       + "to replace a password an administrator set. This discloses everybody's "
                       + "address, so it asks for a permission of its own rather than being implied "
                       + "by being able to raise one.")
            .inputSchema(ArgumentSchema.none())
            .requiredPermission(Permissions.READ_USER)
            .readOnly()
            .handler(invocation -> adminUserService.listUsers().stream().map(UserTool::describe).toList())
            .build();
    }

    private ToolAction get() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("get")
            .title("Read one account")
            .description("Reads one account by the id users_list reports. ⚠️ An email is not an id: "
                       + "the same address can exist once per sign-in provider, so find the account "
                       + "with users_list rather than assuming which one an address means.")
            .inputSchema(ArgumentSchema.builder()
                .requiredString("accountId", "The account's id, as users_list reports it."))
            .requiredPermission(Permissions.READ_USER)
            .readOnly()
            .handler(this::handleGet)
            .build();
    }

    private Object handleGet(ToolInvocation invocation) {
        String accountId = invocation.requiredString("accountId");

        return adminUserService.listUsers().stream()
            .filter(account -> account.getId().equals(accountId))
            .findFirst()
            .map(UserTool::describe)
            // ⚠️ NOTHING_TO_ACT_ON rather than an invalid argument: the id is well formed, it simply
            // names nobody. A model told its argument was invalid retries with a different SHAPE; told
            // there is nothing there, it goes and looks the account up, which is the useful next move.
            .orElseThrow(() -> new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                "There is no account with id '" + accountId + "'. Find one with users_list — an id is "
                + "never invented, and an email address is not one."));
    }

    // ── Raising one ──────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>Not confirmed, unlike revoking a grant.</strong> An account raised by mistake can be
     * blocked in one call and holds nothing until somebody grants it something; the cost of the mistake
     * is a row and a moment, which is not worth a round trip on every legitimate use.
     *
     * <p>⚠️ <strong>Naming roles needs {@code access:administer} as well</strong>, and the service
     * refuses the whole request rather than quietly raising the account without them — see
     * {@code AdminUserService}. The declared permission is the one the route declares; the second,
     * conditional one is asked where the condition is known.
     */
    private ToolAction create() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("create")
            .title("Raise an account")
            .description("Raises a local account with a password you choose, which the person must "
                       + "replace before they can do anything — here or in any application that signs "
                       + "in through Identity. Optionally grants roles at the same time: naming any "
                       + "needs the permission to hand out roles as well, and without it NOTHING is "
                       + "created rather than an account without them. A Google or GitHub identity "
                       + "cannot be raised here — those are created by that provider's first sign-in.")
            .inputSchema(ArgumentSchema.builder()
                .requiredString("email", "The address they sign in with.")
                .optionalString("displayName", "What to call them. Omit for none.")
                .requiredString("initialPassword",
                    "At least 8 characters. They must replace it at first sign-in.")
                .optionalStringList("roles",
                    "Role names to grant at @GLOBAL, as access_read reports them. Omit for an account "
                    + "that holds nothing yet."))
            .requiredPermission(Permissions.CREATE_USER)
            .handler(this::handleCreate)
            .build();
    }

    private Object handleCreate(ToolInvocation invocation) {
        IdentityUser raised = adminUserService.createUser(
            invocation.requiredString("email"),
            invocation.optionalString("displayName").orElse(null),
            invocation.requiredString("initialPassword"),
            invocation.stringList("roles"),
            invocation.actingSubject());

        Map<String, Object> described = describe(raised);

        described.put("roles", invocation.stringList("roles"));

        return described;
    }

    // ── Blocking one ─────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>The reversible half of taking access away, and the only half offered here.</strong>
     * Blocking is undone by the same call with the flag flipped; deleting is not undone at all, which
     * is why it is on the screen and not in this list.
     */
    private ToolAction setEnabled() {
        return ToolAction.builder()
            .toolName(toolName())
            .name("set_enabled")
            .title("Block or unblock an account")
            .description("Blocks an account or lets it back in. A blocked account cannot sign in "
                       + "anywhere, and any protocol connection it holds stops working on its next "
                       + "call. ⚠️ Refused against your own account: it is a guard against locking "
                       + "the installation out of itself, not a permission you are missing.")
            .inputSchema(ArgumentSchema.builder()
                .requiredString("accountId", "The account's id, as users_list reports it.")
                .optionalBoolean("enabled", "False to block, true to let back in. Defaults to blocking."))
            .requiredPermission(Permissions.DISABLE_USER)
            .handler(this::handleSetEnabled)
            .build();
    }

    private Object handleSetEnabled(ToolInvocation invocation) {
        return describe(adminUserService.setEnabled(
            invocation.actingSubject(),
            invocation.requiredString("accountId"),
            invocation.flag("enabled")));
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>No password hash, ever, and no roles.</strong> The first is obvious; the second is the
     * disclosure rule the register follows — who holds what is {@code access:administer}'s question,
     * and answering it here would widen {@code user:read} through a field somebody added for
     * convenience. {@code access_read} is where that lives.
     */
    private static Map<String, Object> describe(IdentityUser account) {
        Map<String, Object> described = new LinkedHashMap<>();

        described.put("id", account.getId());
        described.put("email", account.getEmail());
        described.put("displayName", account.getDisplayName());
        described.put("provider", account.getProvider().name());
        described.put("enabled", account.isEnabled());
        described.put("mustChangePassword", account.isMustChangePassword());

        return described;
    }
}
