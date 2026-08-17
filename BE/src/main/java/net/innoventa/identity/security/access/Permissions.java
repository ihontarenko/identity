package net.innoventa.identity.security.access;

import java.util.List;

/**
 * Every permission this build can be asked about — <strong>and every one of them is about Identity's
 * own screens.</strong>
 *
 * <p>⚠️ <strong>Nothing here is a permission in another product.</strong> "Identity is centralized
 * here; authorization is not" is unchanged by this class existing: these gate who may raise, rename,
 * disable or delete an <em>account</em>, and who may hand out a grant over those powers. None of them
 * is written into a token, and no resource server asks this service what a person may do — Innoventa,
 * Moneta, Tessera and WiQ each answer that from their own rows, as they always have.
 *
 * <p>What this replaces is {@code Role { USER, ADMIN }} plus one filter-chain matcher on
 * {@code /api/admin/**} — a single boolean, with nowhere to say that somebody may add people but not
 * delete them. The cutover itself is a later ticket; this list is the vocabulary it will cut over to.
 *
 * <h2>⚠️ The shape of a name is not a preference</h2>
 *
 * <p>A permission must be {@code namespace:action}. The colon is what the policy parser identifies the
 * shape <em>by</em>, since nothing else distinguishes a permission from one of the language's own
 * keywords — so a single-word name cannot be written down at all.
 *
 * <h2>The test a name had to pass</h2>
 *
 * <p>Each is a switch somebody could plausibly want on <em>without</em> the others. Three of the seven
 * are splits of what is one power today, and each split is the point:
 *
 * <ul>
 *   <li>{@link #READ_USER} is not {@link #CREATE_USER} — the register discloses every account and its
 *       email; raising one does not require the ability to enumerate everybody.
 *   <li>{@link #DISABLE_USER} is not {@link #DELETE_USER} — one is reversible and one is not, and
 *       deletion here is a hard delete with no audit row behind it.
 *   <li>{@link #SET_PASSWORD} is not {@link #EDIT_USER} — setting somebody's password is impersonation
 *       with a delay, because whoever sets it can then sign in as them. Fixing a typo in a display
 *       name is not, and folding the two would mean anybody who may do the second may do the first.
 * </ul>
 */
public final class Permissions {

    /** See the register of accounts. ⚠️ A disclosure surface: every account, and every email. */
    public static final String READ_USER = "user:read";

    /** Raise a new account. */
    public static final String CREATE_USER = "user:create";

    /** Edit an account's profile — display name, email. */
    public static final String EDIT_USER = "user:edit";

    /** Block and unblock an account. The reversible half of taking somebody's access away. */
    public static final String DISABLE_USER = "user:disable";

    /**
     * Delete an account permanently.
     *
     * <p>⚠️ <strong>The irreversible one.</strong> It is deliberately carried by no role — it is granted
     * personally, to a named person, so that the access screen answers <em>who can do this</em> with a
     * list rather than with a role somebody would have to expand.
     */
    public static final String DELETE_USER = "user:delete";

    /**
     * Set another person's password.
     *
     * <p>⚠️ Impersonation with a delay. Whoever holds this can sign in as anybody, one password reset
     * later — which is why it is asked for by name rather than inherited from editing a profile.
     */
    public static final String SET_PASSWORD = "user:password";

    /**
     * Edit the roles this installation shares, and see who holds what.
     *
     * <p>⚠️ <strong>The one that hands out the others.</strong> Editing a role changes it for everybody
     * at once, and reading who holds what is a disclosure surface in its own right.
     */
    public static final String ADMINISTER_ACCESS = "access:administer";

    private static final List<String> ALL = List.of(
        READ_USER,
        CREATE_USER,
        EDIT_USER,
        DISABLE_USER,
        DELETE_USER,
        SET_PASSWORD,
        ADMINISTER_ACCESS);

    /**
     * The catalogue, as the engine's third registration.
     *
     * <p>⚠️ <strong>Nothing on the decision path reads it.</strong> A permission is a bare string where
     * it is <em>asked about</em>. It exists for the two readers that are not the engine: whatever
     * <em>writes</em> a grant, and whatever <em>checks</em> one somebody else wrote — which from ID-3
     * onwards includes the boot-time comparison against {@code policy/identity.jmp} in both directions.
     */
    public static List<String> all() {
        return ALL;
    }

    private Permissions() {
    }

}
