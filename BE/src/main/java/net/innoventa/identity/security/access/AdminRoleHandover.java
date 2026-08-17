package net.innoventa.identity.security.access;

import lombok.RequiredArgsConstructor;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessAdministration.Change;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Hands every account that was an {@code ADMIN} the grants that now mean the same thing.
 *
 * <h2>⚠️ Why this is Java and not a line in a migration</h2>
 *
 * <p>The obvious shape is one Flyway migration that grants and then drops the column. <strong>It
 * cannot work, and the reason is an ordering nothing in the SQL can see:</strong> Flyway runs during
 * context refresh and {@link PolicySeeder} runs after it, so at migration time
 * {@code GLOBAL_ACCESS_ADMINISTRATOR} <em>does not exist as a row yet</em> — there is nothing to
 * assign, and an assignment cannot be written against a role id that has not been minted.
 *
 * <p>Writing the roles into {@code access_roles} from SQL as well would make the migration a second,
 * hand-copied implementation of the policy document. Writing personal grants instead of role
 * assignments would leave every old administrator carrying six overrides on a screen meant for
 * exceptions. So the handover runs here, after the seed, where the roles exist and the port that
 * validates a grant is available.
 *
 * <p>The column is therefore <strong>expanded now and contracted later</strong>: {@code identity_users.role}
 * stays in the database, unmapped and unread by anything but this class, until a release in which
 * every installation has certainly run this. That is the textbook shape for a change like this one and
 * it is the only one that is not a lie about ordering.
 *
 * <h2>It disappears on its own</h2>
 *
 * <p>⚠️ <strong>Nothing has to remember to delete this class.</strong> It asks the database whether the
 * column is still there and does nothing when it is not — so the day the contract migration lands, this
 * becomes a no-op rather than a startup failure, and the deletion is tidying rather than a fix.
 *
 * <p>⚠️ <strong>It runs once per account, not once per start, and the difference is not academic.</strong>
 * {@code assign} reporting "no change" is not the same as not running: a grant somebody had revoked on
 * the access screen would be re-created on the next boot, which is the seed re-asserting itself over an
 * edit — the exact thing {@code AccessStorageConfiguration} refuses the policy document for. So
 * {@link #handOver} clears the {@code role} value it consumed, and the next start finds nothing to do.
 */
@Component
@RequiredArgsConstructor
@Order(AdminRoleHandover.AFTER_THE_POLICY_IS_SEEDED)
public class AdminRoleHandover implements ApplicationRunner {

    /**
     * ⚠️ <strong>After the seed, expressed as a relationship rather than as a number.</strong>
     *
     * <p>This was {@code Integer.MAX_VALUE} — asking to be "last" with the value every un-annotated
     * runner already has. {@link PolicySeeder} had no {@code @Order}, so it held the same value, and
     * two equal orders are a tie broken by bean discovery order: {@code AdminRoleHandover} sorts
     * before {@code PolicySeeder} alphabetically. The handover ran first and the boot died with
     * <em>"There is no role called 'GLOBAL_ACCESS_ADMINISTRATOR'"</em> — a failure that would have
     * been silent had the port not refused an unknown role.
     *
     * <p>Written as the seeder's own constant plus one, so that moving the seeder moves this too.
     */
    static final int AFTER_THE_POLICY_IS_SEEDED = PolicySeeder.SEEDS_BEFORE_ANYTHING_USES_A_ROLE + 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRoleHandover.class);

    /** What an {@code ADMIN} could do, said in the vocabulary that replaced it. */
    private static final List<String> WHAT_ADMIN_MEANT =
        List.of(Roles.GLOBAL_ACCESS_ADMINISTRATOR, Roles.GLOBAL_USER_ADMINISTRATOR);

    private static final String HANDED_OVER = "HANDED_OVER";

    private static final String BY = "bootstrap";

    /**
     * Installation-wide, which is the only place either of these roles may be assigned.
     *
     * <p>{@link ScopeKind#NO_INSTANCE} rather than a value somebody chose: a scope whose nature is
     * "everything" names no instance, and the reference's own constructor rewrites anything else to
     * this — so writing it explicitly says what the row will hold instead of relying on that rewrite.
     */
    private static final ScopeReference EVERYWHERE =
        ScopeReference.of(IdentityScope.GLOBAL, ScopeKind.NO_INSTANCE);

    private final JdbcTemplate         database;
    private final AccessAdministration access;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!roleColumnStillExists()) {
            return;
        }

        List<String> formerAdministrators = database.queryForList(
            "SELECT id FROM identity_users WHERE role = 'ADMIN'", String.class);

        if (formerAdministrators.isEmpty()) {
            LOGGER.debug("🔁 No account still carries the retired ADMIN role — nothing to hand over.");
            return;
        }

        int granted = 0;

        for (String administrator : formerAdministrators) {
            granted += handOver(administrator);
        }

        if (granted == 0) {
            LOGGER.debug("🔁 {} former administrator(s) already hold their grants.",
                formerAdministrators.size());
            return;
        }

        LOGGER.info("🔁 Handed the retired ADMIN role over to {} account(s) as {} grant(s). The "
                    + "`identity_users.role` column is now unread — it is dropped in a later release, "
                    + "once every installation has run this.",
            formerAdministrators.size(), granted);
    }

    /**
     * ⚠️ <strong>Consumes the column value it read, and that is what makes this run once.</strong>
     *
     * <p>Without the final update this method was idempotent in the wrong sense: {@code assign}
     * reports "no change" when the row is already there, so a re-run <em>looked</em> harmless — but a
     * grant somebody had <strong>revoked on the access screen</strong> was simply re-created on the
     * next start. Confirmed by doing exactly that: revoke, restart, and the log cheerfully says
     * "1 grant(s)".
     *
     * <p>That is the precise failure {@code AccessStorageConfiguration} exists to prevent — the seed
     * re-asserting itself over an edit — arriving through a back door nobody was watching. A migration
     * could not have done it, because a migration runs once by construction; this substitute had to
     * earn the same property.
     *
     * <p>So the account stops being an {@code ADMIN} the moment its grants exist. The next start finds
     * no rows to hand over and does nothing, for good.
     */
    private int handOver(String subjectId) {
        int granted = 0;

        for (String role : WHAT_ADMIN_MEANT) {
            Change assigned = access.assign(
                subjectId, role, EVERYWHERE, HANDED_OVER, BY, null);

            if (assigned.changed()) {
                granted++;
            }
        }

        database.update("UPDATE identity_users SET role = 'USER' WHERE id = ?", subjectId);

        return granted;
    }

    /**
     * ⚠️ Asked of the database rather than of a flag, because a flag would have to be kept in step with
     * a migration in another release — and the database is the thing that actually knows.
     *
     * <p>{@code information_schema} is read against the connection's own schema, so it answers about
     * this installation rather than about whichever database happens to be first alphabetically.
     */
    private boolean roleColumnStillExists() {
        Integer found = database.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = ? AND table_name = 'identity_users' AND column_name = 'role'",
            Integer.class,
            currentSchema());

        return found != null && found > 0;
    }

    /**
     * ⚠️ The catalog is called different things by the two dialects this service supports — MySQL's
     * {@code table_schema} is the database name, PostgreSQL's is the schema within it — and
     * {@code Connection.getSchema()} answers correctly for both, which a hard-coded
     * {@code DATABASE()} would not.
     */
    private String currentSchema() {
        return database.execute((ConnectionCallback<String>) connection -> {
            String schema = connection.getSchema();

            return schema == null ? connection.getCatalog() : schema;
        });
    }
}
