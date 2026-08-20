package net.innoventa.identity.service.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.access.Permissions;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessDisclosure;
import org.jmouse.access.projection.PolicyProjection;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyPermissionDeclaration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The access screen's fourth tab: <strong>what is actually in force</strong>, as a {@code .jmp} document.
 *
 * <p>Everything about <em>why</em> is in {@link PolicyProjection}, which is the <strong>library's</strong> as
 * of JMF-17 — this product carried its own copy of it until then, and so did WiQ. This class is the thin
 * half — it fetches
 * the rows and answers the two questions the projection cannot: which scopes to declare, and what to call a
 * scope instance.
 *
 * <h2>⚠️ Both answers are Identity's own, and neither matches the other products'</h2>
 *
 * <p>WiQ's grants sit at {@code @CATEGORY}, which contains itself, so WiQ's own service reads the whole
 * tree to render a path. Identity has no such scope: every holding it writes is at {@code GLOBAL} with
 * {@link ScopeKind#NO_INSTANCE}, so there is nothing to name and no naming is declared — the projection
 * writes the scope as the bare kind it is.
 *
 * <p>⚠️ <strong>And the scope block is rendered from {@code all()}, never {@code floors()}.</strong> A
 * catalogue's floors are the scopes a grant may be written <em>at</em>; Identity's two scopes are not those —
 * {@code GLOBAL} is the widest scope and {@code SELF} is own-rows — so {@code floors()} answers with one of
 * the two and the rendered document would silently declare a vocabulary the engine does not have. The same
 * trap is documented on {@code AccessAdministrationService}, which fell into it first.
 */
@Service
@RequiredArgsConstructor
public class PolicyProjectionService {

    private final AccessAdministration access;
    private final AccessDisclosure     disclosure;
    private final ScopeCatalog         scopes;
    private final PolicyDocument       document;

    @Value("${jmouse.access.policy.name:identity}")
    private String policyName;

    @Transactional(readOnly = true)
    public String render() {
        Map<String, String> described = document.permissions().stream()
            .collect(Collectors.toMap(
                PolicyPermissionDeclaration::name,
                declaration -> declaration.description() == null ? "" : declaration.description(),
                (first, second) -> first));

        return PolicyProjection.of(policyName)
            .permissions(Permissions.all(), described::get)
            .scopes(scopes.all())
            .roles(access.roles())
            .holdings(disclosure.roleHoldings(), disclosure.directHoldings())
            .render();
    }
}
