package net.innoventa.identity.security.access;

import org.jmouse.access.RefusalReason;
import org.springframework.http.HttpStatus;

/**
 * Why a request was refused, and the one place that decides what the client reads.
 *
 * <p><strong>Each refusal keeps its own words.</strong> The status is shared; the sentence is not. Told
 * the same sentence by two different axes, a reader concludes the product is broken rather than that
 * two different things are true.
 *
 * <p>⚠️ <strong>What a refusal says, it says about the caller — never about who else could do it.</strong>
 * <em>"You need {@code user:delete}"</em> is help. <em>"Ask somebody with {@code user:delete}"</em>
 * followed by a name would disclose the register to a caller who does not hold
 * {@link Permissions#READ_USER}, which is the one thing this service must not leak by accident.
 */
public enum AccessReason implements RefusalReason {

    /** Nobody is signed in. The reader's next move: sign in. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, AccessAxis.IDENTITY, "Not signed in"),

    /** The permission is not held anywhere covering this. Next move: ask somebody who can grant it. */
    NO_PERMISSION(HttpStatus.FORBIDDEN, AccessAxis.PERMISSION, "Not yours to do"),

    /**
     * There is no such row, or it is not this caller's to see.
     *
     * <p>⚠️ <strong>Registered rather than reached, and saying so is more honest than implying
     * otherwise.</strong> In Tessera this carries ADR-0002's isolation rule — a person outside a
     * project must not learn it exists, so being outside one reads exactly as a project that is not
     * there. <strong>Identity has no such isolation to keep.</strong> There is no place to be outside
     * of: the register is one list, gated whole on {@link Permissions#READ_USER}, and an account either
     * exists or does not.
     *
     * <p>So this exists because the engine's dispatcher must be told which reason to raise when a route
     * names a target that resolves to nothing — and this service registers no
     * {@code AccessTargetResolver} at all today, which makes that path unreachable. It is a hole
     * plugged with the truthful answer rather than a rule being asserted.
     */
    NOT_FOUND_OR_HIDDEN(HttpStatus.NOT_FOUND, AccessAxis.PERMISSION, "Not found"),

    /**
     * The route promised to publish a value, and the call supplied nothing for it.
     *
     * <p>⚠️ A refusal about the <em>call</em> rather than about the caller, which is why it is a 400:
     * nobody is being told no, the request simply cannot be decided about. Next move: pass the
     * parameter.
     */
    UNDECIDABLE_CALL(HttpStatus.BAD_REQUEST, AccessAxis.PERMISSION, "Not enough to decide");

    private final HttpStatus status;
    private final AccessAxis axis;
    private final String     title;

    AccessReason(HttpStatus status, AccessAxis axis, String title) {
        this.status = status;
        this.axis   = axis;
        this.title  = title;
    }

    /**
     * What the client reads — the single mapping the exception handler will look up.
     *
     * <p>Deliberately <em>not</em> on {@link RefusalReason}. A refusal is a fact about authorization and
     * a status code is a fact about a transport; the engine is called from a method guard today and
     * could be called from a protocol dispatcher tomorrow, and neither should have to know about the
     * other.
     */
    public HttpStatus status() {
        return status;
    }

    @Override
    public AccessAxis axis() {
        return axis;
    }

    /**
     * The refusal a reason is, for the transport code that needs the status mapping above.
     *
     * <p>Every reason this installation produces is one of these already; the lookup by name is there so
     * that one that somehow is not fails at the boundary rather than three frames further in.
     */
    public static AccessReason of(RefusalReason reason) {
        return reason instanceof AccessReason known ? known : valueOf(reason.name());
    }

    /**
     * The heading, distinct per reason.
     *
     * <p>Distinct on purpose: "Access denied" over every one of these is how a reader learns that the
     * product refuses without knowing why.
     */
    public String title() {
        return title;
    }

    /** The machine-readable value carried on the {@code ProblemDetail}, beside the prose. */
    public String wireName() {
        return name().toLowerCase().replace('_', '-');
    }
}
