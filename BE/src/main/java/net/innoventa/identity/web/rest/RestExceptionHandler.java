package net.innoventa.identity.web.rest;

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

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail handleUserNotFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
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
