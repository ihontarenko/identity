package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.domain.IdentityUser;
import net.innoventa.identity.repository.IdentityUserRepository;
import net.innoventa.identity.security.oauth2.Provider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns whatever a person can write down into the identifier a grant is keyed on.
 *
 * <h2>⚠️ Why this exists at all, and it is not politeness</h2>
 *
 * <p>{@code policy/identity.jmp} writes {@code assign subject ${identity.bootstrap.owner}}, and that
 * property holds whatever somebody could know <em>before</em> looking in the database — an email
 * address, realistically. <strong>Every grant in this installation is keyed on the row id.</strong>
 * Written straight through, an email would produce a row whose {@code subject_id} is
 * {@code ihontarenko@gmail.com} while every lookup asks for {@code SU} — and it would parse, seed,
 * project onto the access screen, and grant absolutely nothing.
 *
 * <p>That is not hypothetical: it is exactly what happened in Tessera, where the raw handle sat in
 * {@code access_role_assignments} looking like an answer. Resolving at seed time is the fix that needs
 * no library change — the seed is what turns a document into rows, so it is the right place to turn a
 * name into an identifier.
 *
 * <h2>What a handle may be</h2>
 *
 * <p>A row id first, then a {@code LOCAL} email. In that order, because an id is unambiguous and an
 * email is not — the same address can have separate {@code LOCAL}, {@code GOOGLE} and {@code GITHUB}
 * rows, which is a known and accepted gap in this service rather than something to guess around.
 *
 * <p>⚠️ <strong>{@code LOCAL} only, and deliberately.</strong> Naming a Google identity as the
 * installation's owner would tie the way back in to a third party's continued cooperation. The
 * bootstrap owner is the account that can always sign in with a password held here.
 */
@Component
@RequiredArgsConstructor
public class SubjectHandles {

    private final IdentityUserRepository identityUserRepository;

    /** The account a handle names, or nothing where it names nobody. */
    public Optional<IdentityUser> resolve(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.empty();
        }

        String trimmed = handle.trim();

        return identityUserRepository.findById(trimmed)
            .or(() -> identityUserRepository.findByEmailAndProvider(trimmed, Provider.LOCAL));
    }
}
