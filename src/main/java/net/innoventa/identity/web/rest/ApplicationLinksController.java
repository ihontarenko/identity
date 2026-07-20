package net.innoventa.identity.web.rest;

import lombok.RequiredArgsConstructor;
import net.innoventa.identity.config.IdentityProperties;
import net.innoventa.identity.web.rest.dto.ApplicationLinksResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feeds the landing page's "go to this application" links. Public (no session required) since the
 * landing page itself is the first thing an unauthenticated visitor sees. Reuses each client's
 * already-configured {@code postLogoutRedirectUri} as its base URL — replaces
 * {@code web.HomeViewController}.
 */
@RestController
@RequiredArgsConstructor
public class ApplicationLinksController {

    private final IdentityProperties identityProperties;

    @GetMapping("/api/application-links")
    public ApplicationLinksResponse applicationLinks() {
        return new ApplicationLinksResponse(clientUrl("innoventa"), clientUrl("moneta"), clientUrl("central"));
    }

    private String clientUrl(String clientKey) {
        IdentityProperties.ClientProperties client = identityProperties.clients().get(clientKey);
        return client != null ? client.postLogoutRedirectUri() : null;
    }

}
