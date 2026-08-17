package net.innoventa.identity.security.access;

import org.jmouse.access.AccessDecision;

/**
 * <em>You may not</em> — one idea, one exception, and the reason that decides what the client reads.
 *
 * <h2>⚠️ Named {@code Refused} rather than {@code Denied}, and only here</h2>
 *
 * <p>Tessera's equivalent is {@code AccessDeniedException}, which is the better name in the abstract
 * and the wrong one in <strong>this</strong> service. Identity is the authorization server: Spring
 * Security is everywhere in it, and {@code org.springframework.security.access.AccessDeniedException}
 * is a class its own filter chains handle. Two types of that name, one of them in a package called
 * {@code security.access}, is a wrong import that compiles — in exception handling, where a wrong
 * import means a refusal quietly taking the other branch.
 *
 * <p>So the name differs from the sibling product on purpose. Everything else about it does not.
 *
 * <p>The status hangs off the {@link AccessReason} the engine's axis produced, and the REST advice
 * looks it up in one place. What each refusal keeps is its <strong>own words</strong>: the status is
 * shared, the sentence is not.
 */
public class AccessRefusedException extends RuntimeException {

    private final AccessReason reason;

    public AccessRefusedException(AccessReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * The refusal an engine decision already carries, thrown without rewording it.
     *
     * <p>The decision hands back the model's open {@code RefusalReason}; this exception holds
     * Identity's constant, because what it exists for is the status mapping, and the status is this
     * product's.
     */
    public static AccessRefusedException of(AccessDecision decision) {
        return new AccessRefusedException(AccessReason.of(decision.reason()), decision.words());
    }

    public AccessReason getReason() {
        return reason;
    }

    public AccessAxis getAxis() {
        return reason.axis();
    }
}
