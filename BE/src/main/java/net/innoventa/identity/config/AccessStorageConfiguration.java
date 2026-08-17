package net.innoventa.identity.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.jpa.JpaGrantStore;
import org.jmouse.access.jpa.StoredConditions;
import org.jmouse.access.spi.GrantStore;
import org.jmouse.access.spring.jpa.AccessJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Where Identity says which of the two possible homes a grant actually has.
 *
 * <p>Everything else about the access tables — the entities, the queries, the administration ports, the
 * disclosure — is {@code jmouse-access-jpa}'s and is auto-configured. <strong>One bean, and it is
 * load-bearing.</strong>
 *
 * <h2>⚠️ The rows, alone — the composition with the document is refused</h2>
 *
 * <p>{@code AccessPolicyAutoConfiguration} composes every {@link GrantStore} bean with the policy
 * document's own and marks the composite {@code @Primary}. That is right for a product with genuinely
 * two sources. Identity will not be one: from ID-3 onwards {@code policy/identity.jmp} is the
 * <em>seed</em> — what a fresh installation is born with — and everything the engine reads afterwards
 * is a row.
 *
 * <p>Leaving the composition in place would not be merely redundant. The document still loads, because
 * the seed and the access screen's projection both need it, so every role it declares would be unioned
 * back over the tables — and a permission taken off a role through the access screen would go on being
 * granted by the file it was seeded from. Which is exactly the failure the whole arrangement exists to
 * make impossible.
 *
 * <p>⚠️ <strong>This is written before the document exists, on purpose.</strong> No policy locations are
 * configured yet, so that auto-configuration does not even switch on and there is nothing to refuse
 * today. Adding the refusal at the same time as the document would mean the first boot that reads a
 * policy is also the first boot that could get this wrong — and getting it wrong is invisible until
 * somebody revokes something and it comes back after a restart.
 *
 * <p>⚠️ <strong>Registering the same bean under the composed name is what makes that condition back
 * off.</strong> Naming only {@code jpaGrantStore} leaves both beans standing, both primary, and the
 * failure is <em>"more than one 'primary' bean"</em> several frames inside a security filter.
 *
 * <p>⚠️ <strong>Constructed here rather than injected by name.</strong>
 * {@code AccessJpaAutoConfiguration.jpaGrantStore} is {@code @ConditionalOnMissingBean} on the
 * <em>type</em>, and a user configuration is processed before auto-configuration — so declaring any
 * {@link GrantStore} here makes that one back off, and a qualifier naming it would name a bean nobody
 * created. Constructing it is also the honest description: there is one store, and this is it.
 *
 * @see AccessVocabularyConfiguration for the vocabulary half — scopes, axes, permissions
 */
@Configuration
public class AccessStorageConfiguration {

    /** The name {@code AccessPolicyAutoConfiguration} declares its composite under, claimed here. */
    private static final String COMPOSED_STORE = "accessGrantStore";

    @Bean(name = {AccessJpaAutoConfiguration.STORED_GRANTS, COMPOSED_STORE})
    @Primary
    public GrantStore accessGrantStore(
        EntityManagerFactory entityManagers, ScopeCatalog scopes, StoredConditions conditions) {

        return new JpaGrantStore(sharedEntityManager(entityManagers), scopes, conditions);
    }

    /**
     * ⚠️ A factory, because Spring Boot registers no plain {@link EntityManager} bean — asking for one
     * here compiles and then fails at startup, several frames into Tomcat's boot, naming a security
     * filter rather than this class.
     *
     * <p>The shared manager is the right one to hand a store in any case: it resolves to whatever
     * manager the current transaction is using, so a read inside one sees that transaction's writes.
     */
    private static EntityManager sharedEntityManager(EntityManagerFactory entityManagers) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagers);
    }
}
