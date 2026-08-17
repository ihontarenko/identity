package net.innoventa.identity.web.rest;

import net.innoventa.identity.security.access.AccessReason;
import net.innoventa.identity.security.access.AccessRefusedException;
import net.innoventa.identity.service.BusinessRuleViolationException;
import net.innoventa.identity.service.InvalidCurrentPasswordException;
import net.innoventa.identity.service.SelfModificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes the JSON error mapping for exceptions that mean something specific to more than one
 * REST controller under {@code web.rest}, rather than repeating the same {@code @ExceptionHandler}
 * per controller. Returns {@link ProblemDetail} (RFC 7807, same convention as Innoventa/BE and
 * Moneta/BE) instead of throwing a fresh {@code ResponseStatusException} from within the
 * handler method — that seemed equivalent but isn't: an exception thrown from inside an
 * {@code @ExceptionHandler} doesn't get re-resolved by {@code ResponseStatusExceptionResolver}, it
 * escapes as a raw, unmapped {@code 500} (confirmed by actually driving these three failure paths
 * against a running instance, not just reading the code).
 */
@RestControllerAdvice(basePackageClasses = RestExceptionHandler.class)
class RestExceptionHandler {

    /**
     * A refusal from the access engine, in the words the axis that refused chose.
     *
     * <p>⚠️ <strong>The status hangs off the reason, not off this method.</strong> Being unauthenticated
     * is a 401 and holding no permission is a 403 — two different next moves for the reader, and the
     * engine is what knows which happened. A single {@code @ResponseStatus(FORBIDDEN)} here would tell
     * somebody who is simply signed out to go and ask an administrator.
     *
     * <p>{@code reason} and {@code axis} travel beside the prose so an interface can tell them apart
     * without parsing a sentence somebody may reword.
     *
     * <p>⚠️ The sentence names the permission that was missing and <strong>never who holds it</strong> —
     * pointing at a person would disclose the register to a caller without {@code user:read}.
     */
    @ExceptionHandler(AccessRefusedException.class)
    ProblemDetail handleAccessRefused(AccessRefusedException exception) {
        AccessReason  reason  = exception.getReason();
        ProblemDetail problem = ProblemDetail.forStatus(reason.status());

        problem.setTitle(reason.title());
        problem.setDetail(exception.getMessage());
        problem.setProperty("reason", reason.wireName());
        problem.setProperty("axis", exception.getAxis().name().toLowerCase());

        return problem;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleUserNotFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
    }

    /**
     * The request was well formed and the caller was entitled to make it — the answer is that what it
     * asks for is not a thing this build can do.
     *
     * <p>⚠️ Kept apart from {@code AccessRefusedException} on purpose: a 403 tells somebody to go and
     * ask for a permission, and this tells them to fix what they sent. Told the same thing by both, a
     * reader learns nothing from either.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleInvalidCurrentPassword() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }

    @ExceptionHandler(SelfModificationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail handleSelfModification() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "You cannot modify your own account here");
    }

}
