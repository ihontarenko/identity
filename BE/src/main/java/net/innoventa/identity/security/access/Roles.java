package net.innoventa.identity.security.access;

/**
 * The role names {@code policy/identity.jmp} declares, for the code that has to hand one out.
 *
 * <p>⚠️ <strong>Nothing checks these against the document</strong>, unlike {@link Permissions}, which
 * is compared in both directions at boot. The asymmetry is deliberate: a mistyped <em>permission</em>
 * grants nothing silently, which is why it needs a guard; a mistyped <em>role</em> fails loudly at the
 * moment somebody is assigned it, because {@code AccessAdministration.assign} refuses a role that does
 * not exist. Loud is enough.
 */
public final class Roles {

    /** Everything about accounts, and nothing about who may hand out powers. */
    public static final String GLOBAL_USER_ADMINISTRATOR = "GLOBAL_USER_ADMINISTRATOR";

    /**
     * The installation's way back in — editing the shared roles and reading who holds what.
     *
     * <p>Deliberately not a superuser: it can read the register, and it can create, disable, rename
     * or delete nobody.
     */
    public static final String GLOBAL_ACCESS_ADMINISTRATOR = "GLOBAL_ACCESS_ADMINISTRATOR";

    private Roles() {
    }
}
