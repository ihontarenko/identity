package net.innoventa.identity.security.access;

import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeNature;

import java.util.Optional;

/**
 * Where a permission means something in Identity.
 *
 * <p><strong>Two floors, and the shortest ladder of any product in this workspace.</strong> Tessera
 * holds grants at a project; WiQ at a category, which contains its own subtree. Identity holds them at
 * the installation, or at the rows a person owns, and there is nothing in between.
 *
 * <ul>
 *   <li>{@link #GLOBAL} — the installation. Every model needs exactly one {@code EVERYTHING} scope, and
 *       it is the link in the covering chain that always matches, so an unscoped grant means something.
 *   <li>{@link #SELF} — the narrowest thing a grant can be about: a person's own row. It earns its
 *       place immediately rather than being registered ahead of a first use — self-service account
 *       management already exists and is separated from the administrative half by nothing but a URL
 *       prefix today.
 * </ul>
 *
 * <h2>⚠️ Why there is no {@code USER} scope</h2>
 *
 * <p>"Administer <em>these</em> users" is the tempting third floor and it cannot be filled. It would
 * need a grouping this service does not have — no organizations, no tenants, no teams — so the scope
 * would be declarable and unnameable, which puts an instance in the covering chain that never resolves:
 * a grant that parses, projects onto a screen, and decides nothing. If a grouping ever arrives, the
 * floor arrives with it.
 *
 * <h2>⚠️ Why neither floor takes a request parameter</h2>
 *
 * <p>A parameter is how a route spells its place into the URL so the binder can read it without naming
 * a resource. Neither of these has a place to spell: the installation is everywhere, and a person's own
 * rows are wherever they are. So every route here decides at {@code GLOBAL} or about the caller, and
 * this service needs no {@code AccessTargetResolver} at all.
 *
 * <p><strong>Declaration order is width order</strong> — {@link #rank()} is {@code ordinal()} and there
 * is deliberately no rank column. A floor in the wrong position is a covering chain nobody reordered.
 */
public enum IdentityScope implements ScopeKind {

    /** Everything. Exactly one scope must be this, and it is what makes an unscoped grant meaningful. */
    GLOBAL(ScopeNature.EVERYTHING, null),

    /** The rows a person owns — their own account, and what self-service acts on. */
    SELF(ScopeNature.OWN_ROWS, null);

    private final ScopeNature nature;
    private final String      requestParameter;

    IdentityScope(ScopeNature nature, String requestParameter) {
        this.nature           = nature;
        this.requestParameter = requestParameter;
    }

    public static IdentityScope of(ScopeKind kind) {
        return kind instanceof IdentityScope scope ? scope : valueOf(kind.name());
    }

    @Override
    public ScopeNature nature() {
        return nature;
    }

    @Override
    public int rank() {
        return ordinal();
    }

    @Override
    public Optional<String> requestParameter() {
        return Optional.ofNullable(requestParameter);
    }
}
