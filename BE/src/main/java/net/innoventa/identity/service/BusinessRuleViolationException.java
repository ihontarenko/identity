package net.innoventa.identity.service;

/**
 * A request that is well formed, from somebody entitled to make it, asking for something the domain
 * will not do.
 *
 * <p>⚠️ <strong>Not an access refusal.</strong> {@code AccessRefusedException} means <em>you may
 * not</em>; this means <em>that is not a thing</em> — a permission no build declares, a bundle line
 * naming a scope nobody registers, a personal grant with no reason. The two must stay apart, because a
 * 403 tells somebody to go and ask for a permission and this tells them to fix what they sent.
 *
 * <p>It exists rather than a {@code ResponseStatusException} thrown from a service because the service
 * layer here is deliberately free of HTTP concerns — the same reason {@code AccountService} has none.
 * The status is chosen once, in {@code RestExceptionHandler}.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
