package net.innoventa.identity.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React SPA shell for the client-side routes React Router owns. The build output lives
 * directly under {@code src/main/resources/static} (see {@code Identity/UI}'s {@code vite.config.ts}
 * {@code outDir}), so Spring Boot's default static resource handler already serves {@code /} (its
 * welcome-page behavior finds {@code static/index.html} automatically) and {@code /assets/**}; this
 * controller only covers routes that have no matching static file. A forward, not a redirect, so the
 * browser's address bar and any deep link a visitor actually requested stay intact.
 */
@Controller
public class SinglePageApplicationController {

    @GetMapping({"/login", "/account", "/admin/**", "/settings/**"})
    public String forwardToApplicationShell() {
        return "forward:/index.html";
    }

}
