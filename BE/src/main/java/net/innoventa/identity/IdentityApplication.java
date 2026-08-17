package net.innoventa.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ⚠️ <strong>The entity scan is widened by hand because a library owns tables this service stores rows
 * in.</strong> {@code jmouse-access-jpa} owns every {@code access_*} table: the roles, their bundles,
 * who holds them, and the personal allow or deny that beats them.
 *
 * <p><strong>Whoever owns the table owns the mapping.</strong> An Identity {@code @Entity} over the
 * library's schema would be kept honest only by {@code ddl-auto: validate}, which is a hope rather than
 * a contract — so there is none.
 *
 * <p>⚠️ Forgetting this line fails late and misleadingly. Hibernate validates only what it was given,
 * so {@code ddl-auto: validate} passes, the service starts, and the first query dies with <em>"could
 * not resolve root entity"</em> — from inside a library, on whichever request reaches it first. The
 * tables themselves will be there, because the library brings its own migrations.
 *
 * <p>⚠️ Naming {@code net.innoventa.identity} explicitly is not optional once this annotation is
 * present: {@code @EntityScan} <em>replaces</em> the default package scan rather than adding to it, so
 * listing only the library's package would leave {@code IdentityUser} unmapped.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan({"net.innoventa.identity", "org.jmouse.access.jpa.entity"})
public class IdentityApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(IdentityApplication.class, arguments);
    }

}
