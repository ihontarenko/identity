package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import org.jmouse.access.Subject;
import org.jmouse.access.enforcement.CurrentSubject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is asking, as the engine understands it.
 *
 * <p><strong>The subject identifier is the {@code identity_users} row id, and here that costs
 * nothing.</strong> This is the one place Identity is structurally simpler than every other product in
 * this workspace: Tessera and WiQ resolve a local member row from a token's {@code sub} on first
 * sign-in, which is why they need a provisioner and why their bootstrap grant has to fire at two
 * different moments. Here the authenticated principal already <em>is</em> the row every grant is keyed
 * on — {@code IdentityUserDetailsService} has returned the id as the username since the provider column
 * landed, precisely so that an ambiguous email could never become a subject.
 *
 * <p>So there is nothing to provision, nothing to translate, and no lookup table between the person
 * signing in and the person a grant names.
 *
 * <h2>⚠️ Anonymous is a token, not an absence</h2>
 *
 * <p>Spring Security's {@link AnonymousAuthenticationToken} reports {@code isAuthenticated() == true} —
 * checking only that flag would hand the engine {@code "anonymousUser"} as a subject id, which resolves
 * an empty permission set and therefore <em>looks</em> right while meaning something else entirely. It
 * is excluded by type.
 *
 * <p>A session naming a row that no longer exists reads as anonymous too. That is the honest answer:
 * an account that has been deleted holds nothing, whatever cookie is still in the browser.
 *
 * <h2>The description, and why it is worth a lookup</h2>
 *
 * <p>{@link Subject#description()} is what a debug line and the access screen print. It names the person
 * rather than their key on purpose — an authorization log reading {@code 4f2a-…} is a log nobody can use
 * during an incident. The row is read at most once per request ({@link AccessContext#subject}), and off
 * a request it is read per call, which is the same trade the resolution cache makes and for the same
 * reason.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserSubject implements CurrentSubject {

    private final IdentityUserRepository        identityUserRepository;
    private final ObjectProvider<AccessContext> request;

    @Override
    public Subject get() {
        AccessContext current = AccessContext.current(request);

        return current == null ? resolve() : current.subject(this::resolve);
    }

    /**
     * One account, as a subject.
     *
     * <p>Public because whatever acts <em>on behalf of</em> a person — an administrative call, and later
     * a protocol tool — has the row rather than the session, and must produce the same subject the
     * session would have produced. Two ways to describe one person is how an audit line and a grant stop
     * agreeing.
     */
    public static Subject of(IdentityUser identityUser) {
        return identityUser == null
            ? Subject.anonymous()
            : Subject.of(identityUser.getId(), describe(identityUser));
    }

    private Subject resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Subject.anonymous();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return Subject.anonymous();
        }

        return identityUserRepository.findById(authentication.getName())
            .map(CurrentUserSubject::of)
            .orElseGet(Subject::anonymous);
    }

    private static String describe(IdentityUser identityUser) {
        return identityUser.getEmail() == null ? identityUser.getDisplayName() : identityUser.getEmail();
    }
}
