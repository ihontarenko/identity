package net.innoventa.identity.mcp;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.jmouse.ai.mcp.authorization.server.ApprovingSubject;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Who is signed in on the consent screen, and what a client may be authorized to act as.
 *
 * <h2>⚠️ One choice, and it is the person themselves</h2>
 *
 * <p>The library's port was written to allow two shapes, and this is the simpler one: Tessera's people
 * own <em>agent accounts</em> with permissions of their own, so choosing between them is the most
 * important thing on its screen. Identity has no such object — a protocol credential acts as the person
 * who approved it and nothing else — so the list has exactly one entry and the screen shows no picker.
 *
 * <p>It follows that the epic's invariant holds here <strong>by construction rather than by a check</strong>:
 * an agent driving this endpoint is bounded by the same grants the person's browser is, because it is
 * the same subject. There is no persona to widen and none to switch off separately.
 *
 * <p>⚠️ <strong>Never {@code newSubject}.</strong> That exists for a product whose person owns nothing
 * yet and would otherwise face an empty screen. Here somebody signed in always has exactly one thing to
 * offer — themselves — so an "make one" entry would be an option that means nothing.
 *
 * <h2>⚠️ The session, not a token</h2>
 *
 * <p>This resolves over Identity's own security context, which is a <strong>session cookie</strong>. It
 * is why {@code jmouse.mcp.authorization.consent.token-storage-key} is deliberately left unset: the
 * shared consent page reads that key to find a bearer token, finds none here, and falls back to sending
 * the origin's cookies. Setting it to anything would make the page look for a token this interface does
 * not keep and refuse everybody as signed out.
 */
@Component
@RequiredArgsConstructor
public class IdentityApprovingSubject implements ApprovingSubject {

    private final IdentityUserRepository accounts;

    @Override
    public Approver current() {
        IdentityUser account = signedIn();

        return new Approver(
            account.getDisplayName() == null ? account.getEmail() : account.getDisplayName(),
            account.getEmail(),
            List.of(Choice.of(
                account.getId(),
                account.getDisplayName() == null ? account.getEmail() : account.getDisplayName(),
                account.getEmail())));
    }

    private IdentityUser signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {

            throw new McpAuthorizationException(
                "Nobody is signed in. This page grants your own access, so it can only be used from a "
                + "signed-in session.");
        }

        return accounts.findById(authentication.getName())
            .orElseThrow(() -> new McpAuthorizationException(
                "The signed-in session names an account that no longer exists."));
    }
}
