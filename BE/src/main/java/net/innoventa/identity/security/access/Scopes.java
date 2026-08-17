package net.innoventa.identity.security.access;

/**
 * Identity's two scopes, by name — for the one place a constant is needed and an enum cannot go.
 *
 * <p>{@code @RequiresAccess} lives in {@code jmouse-access-enforcement}, and an annotation attribute
 * has to be a constant of a type the annotation declares. A library cannot declare this product's
 * scope enum, so the attribute is a {@code String} and these are the strings.
 *
 * <p>What is lost is the compiler checking the value; what replaces it is the library resolving every
 * name against the {@link org.jmouse.access.ScopeCatalog} as it reads the declaration. A typo fails
 * the boot rather than silently widening a route — later than the compiler, but before anybody can
 * call it.
 *
 * <p>Each constant is the {@link IdentityScope} of the same name, and they cannot drift: the catalogue
 * is built from {@code IdentityScope.values()}, so a name here that is not a constant there stops the
 * application.
 */
public final class Scopes {

    /** Everything. What every administrative route in this service declares, there being no narrower place. */
    public static final String GLOBAL = "GLOBAL";

    /** The rows the caller owns — their own account. */
    public static final String SELF = "SELF";

    private Scopes() {
    }
}
