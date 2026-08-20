package net.innoventa.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import net.innoventa.identity.security.oauth2.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the Spring Authorization Server. The RSA signing key below is generated fresh on every
 * restart, which is fine for local development (existing tokens simply become unverifiable) but
 * must be replaced with a persisted key pair before this service is deployed anywhere shared,
 * since every resource server trusts these keys via JWKS.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final IdentityProperties identityProperties;

    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        httpSecurity
            .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            .with(authorizationServerConfigurer, configurer -> configurer.oidc(Customizer.withDefaults()))
            // These endpoints (/oauth2/token, /oauth2/introspect, /oauth2/revoke, ...) are called
            // server-to-server by confidential clients authenticating with Basic auth, never by a
            // browser form submission — CSRF protection has nothing to defend here and only blocks
            // legitimate token requests. The human-facing /login page (a separate filter chain)
            // keeps CSRF protection.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            // ⚠️ THIS CHAIN IS THE ONE THAT MATTERS FOR THE FLAG. An account holding a password its
            // administrator chose must not be able to trade it for a token at /oauth2/authorize —
            // otherwise it walks into every product that trusts this service, and the flag is
            // decoration.
            //
            // ⚠️ AFTER SecurityContextHolderFilter, AND THE POSITION IS THE WHOLE THING. Written
            // `addFilterAfter(..., BasicAuthenticationFilter.class)` — the obvious choice, and the one
            // the default chain below uses — it never ran at all here: Spring Authorization Server
            // registers OAuth2AuthorizationEndpointFilter around the pre-authenticated position, which
            // is EARLIER in the standard order, so the endpoint had already issued a code and
            // committed a 302 before this filter was reached. Confirmed by driving it: a flagged
            // account got `Location: …?code=…` for tessera-web. Placing it immediately after the
            // context is loaded is what makes it the first thing an authenticated request meets.
            .addFilterAfter(passwordChangeRequiredFilter, SecurityContextHolderFilter.class)
            .exceptionHandling(exceptionHandling -> exceptionHandling.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                browserNavigation()))
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return httpSecurity.build();
    }

    /**
     * Form login is gone — the SPA authenticates via a JSON {@code POST /api/authentication/login}
     * ({@code web.rest.AuthenticationController}), not a submitted {@code <form>}, so there is no
     * {@code .formLogin(...)} DSL call here anymore. CSRF protection stays on (this is still a
     * session-cookie chain), just delivered the way a browser SPA can consume it: a readable
     * {@code XSRF-TOKEN} cookie ({@link CookieCsrfTokenRepository#withHttpOnlyFalse()}) that the
     * SPA echoes back as an {@code X-XSRF-TOKEN} header, resolved by {@link SpaCsrfTokenRequestHandler}
     * and actually written to the response by {@link CsrfCookieFilter} (Spring Security 6's token
     * resolution is lazy and a pure-JSON application never triggers it otherwise). {@code /oauth2Login} is
     * untouched — Google/GitHub sign-in stays a server-driven redirect dance regardless of how the
     * SPA renders its "Sign in with Google" button.
     *
     * <p>Every page route below is {@code permitAll} because the SPA shell itself is public; actual
     * authorization happens per-API-call ({@code /api/account/**} requires a session, {@code
     * /api/admin/**} requires {@code ROLE_ADMIN}) and the SPA's own router redirects an unauthenticated
     * visitor client-side after asking {@code GET /api/me}. An unauthenticated hit on a guarded API
     * path gets a plain {@code 401} — not a redirect to {@code /login} — since the caller is `fetch`,
     * not a browser navigation.
     */
    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(
        HttpSecurity httpSecurity,
        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler
    ) throws Exception {
        httpSecurity
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                // ⚠️ THE PROTOCOL'S OWN POSTS ARE EXEMPT, AND THEY HAVE TO BE. CSRF defends a browser
                // that submits on a person's behalf without their intent; the caller here is a
                // command-line program with no cookie and no origin, which has nowhere to read an
                // XSRF token from and nothing for one to protect. Left on, registration and the token
                // exchange are refused before they reach a handler — and the symptom is a 302 to the
                // login page, which a client reports as "the server does not support registration".
                //
                // ⚠️ /review and /approve are NOT exempt: those ARE browser posts, made by a signed-in
                // person on the consent screen, and they are exactly what CSRF is for.
                .ignoringRequestMatchers(
                    "/api/agents/authorization/register",
                    "/api/agents/authorization/token"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Same position as the authorization-server chain above, and for the same reason — one
            // answer to "where does this go", rather than two that have to be kept in step.
            .addFilterAfter(passwordChangeRequiredFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/.well-known/**").permitAll()
                // Reachable by an unauthenticated visitor by construction (initiates/completes the
                // Google/GitHub handshake before Identity has any session for them yet) — don't
                // assume .oauth2Login(...) auto-permits these the way .formLogin(...).permitAll()
                // used to for the login page; verified by actually driving the flow in a browser,
                // not just reading the docs (see this class's own history of chain gotchas above).
                .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/authentication/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/application-links").permitAll()
                // The Model Context Protocol's authorization walk. ⚠️ Public BY NECESSITY: a client
                // reads these before it holds anything, which is what they are for.
                //
                // ⚠️ REGISTER, AUTHORIZE AND TOKEN ONLY — the consent screen's own /review and
                // /approve are NOT here, and must not be. They are what a signed-in person uses to
                // grant their own access, so they fall through to `.anyRequest().authenticated()`
                // below and are answered by the session cookie. Permitting them would let anybody who
                // can reach this host approve a client against somebody else's account.
                .requestMatchers(HttpMethod.POST, "/api/agents/authorization/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/agents/authorization/authorize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/agents/authorization/token").permitAll()
                // The screen itself is a page; what it does still needs a session.
                .requestMatchers(HttpMethod.GET, "/api/agents/authorization/consent").permitAll()
                .requestMatchers(
                    "/", "/login", "/account", "/admin/**", "/settings/**",
                    "/assets/**", "/favicon.ico", "/favicon.svg", "/index.html")
                .permitAll()
                // ⚠️ `/api/admin/** → hasRole("ADMIN")` USED TO SIT HERE, AND ITS ABSENCE IS THE
                // POINT. It was the real enforcement point for the whole administrative surface —
                // AdminUserController's own javadoc said so — which meant one boolean decided six
                // different powers, and an installation wanting somebody who may add people but not
                // delete them had nowhere to say so.
                //
                // Every one of those endpoints now declares the permission it actually needs, and the
                // access engine answers. The consequence to keep in mind: a controller method under
                // /api/admin with no @RequiresAccess is reachable by any signed-in caller. There is no
                // longer a blanket rule underneath to catch it.
                .anyRequest().authenticated())
            .exceptionHandling(exceptionHandling -> exceptionHandling
                // Same split as authorizationServerSecurityFilterChain above, and for the same
                // reason: a browser navigating straight to a protected, non-API path (swagger-ui,
                // actuator) should land on the branded /login page and bounce back via
                // the saved-request cache after signing in, not dead-end on a bare 401 — which,
                // discovered by actually hitting such a page while logged out rather than only
                // testing /api/** calls, Spring Boot turns into its default Whitelabel Error Page
                // once request.sendError(401) forwards to /error with no Thymeleaf left to render a
                // custom one. A fetch/XHR call (Accept: application/json) still gets a plain 401,
                // since redirecting a JSON caller into an HTML login page would be worse.
                // Registered as two defaultAuthenticationEntryPointFor(...) matchers, evaluated in
                // this order, rather than one matcher plus a plain .authenticationEntryPoint(...)
                // fallback — that second shape looked equivalent but isn't: an explicit
                // .authenticationEntryPoint(...) call silences every defaultAuthenticationEntryPointFor
                // registration outright (confirmed by driving both request shapes against a running
                // instance: the HTML request still got a bare 401 instead of the expected redirect).
                //
                // ⚠️ /api/** IS REGISTERED FIRST AND NEVER REDIRECTS, whatever it says it accepts. The
                // media type is a hint about a browser's intent; a path under /api is a fact about what
                // the caller is. See browserNavigation() for the failure that made this belt as well as
                // braces — a redirected /api/me is not a failed call, it is a SUCCESSFUL one carrying
                // the wrong thing, and no client can defend itself against that.
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**"))
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    browserNavigation())
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    AnyRequestMatcher.INSTANCE))
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(oAuth2LoginSuccessHandler));

        return httpSecurity.build();
    }

    /**
     * A request that is a person navigating, rather than a program calling.
     *
     * <h2>⚠️ Why {@code new MediaTypeRequestMatcher(TEXT_HTML)} on its own is a trap</h2>
     *
     * <p>It ignores nothing by default, and {@code text/html} is <em>compatible with</em> {@code *&#47;*}.
     * Every {@code fetch}/XHR library sends {@code *&#47;*} somewhere in its Accept header — axios sends
     * {@code application/json, text/plain, *&#47;*} on every request this SPA makes — so the matcher
     * answered <em>yes, this is a browser navigating</em> for literally every call the interface made.
     *
     * <p><strong>The failure that produced was a blank application, and it was blank rather than broken
     * for a reason worth understanding.</strong> Signed out, {@code GET /api/me} was answered with a 302
     * to {@code /login}; the browser followed it; {@code /login} is served by the SPA fallback, so the
     * call came back <strong>200 with an HTML document</strong>. Axios does not know that was not the
     * answer to the question — it resolves, and {@code AuthContext} receives a non-empty string where a
     * user belongs. {@code user.permissions.includes(…)} then threw during render, React unmounted the
     * root, and the page went to bare background. ⚠️ Every visitor who was not signed in saw that.
     *
     * <p>Ignoring {@link MediaType#ALL} is what makes the matcher mean what it says: only a caller that
     * asked for {@code text/html} <em>specifically</em> is sent to the login page. Everything else gets a
     * 401, which is a thing a client can act on.
     *
     * <p>⚠️ This is the third time this service has produced a redirect where a status was wanted — see
     * the {@code sendError} note in {@code McpConfiguration} and the CSRF one above. The pattern is not
     * about any one endpoint: it is that <strong>Identity has a login page at all</strong>, so anything
     * unauthenticated here has somewhere to be sent, and being sent there looks like success.
     */
    private static MediaTypeRequestMatcher browserNavigation() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);

        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));

        return matcher;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        List<RegisteredClient> registeredClients = identityProperties.clients().values().stream()
            .map(client -> buildRegisteredClient(client, passwordEncoder))
            .toList();

        return new InMemoryRegisteredClientRepository(registeredClients);
    }

    /**
     * A {@code publicClient} (browser SPA — no secret can ever be kept safe in shipped JS) gets
     * {@code ClientAuthenticationMethod.NONE} plus mandatory PKCE ({@code requireProofKey}), which
     * replaces the secret as proof of client identity. Everything else (innoventa today) keeps the
     * original confidential-client shape: a secret exchanged via HTTP Basic, no PKCE requirement.
     */
    private static RegisteredClient buildRegisteredClient(IdentityProperties.ClientProperties client, PasswordEncoder passwordEncoder) {
        RegisteredClient.Builder registeredClientBuilder = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(client.clientId())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(client.publicClient())
                .setting("audience", client.audience())
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .build());

        // ⚠️ EVERY ADDRESS THE INTERFACE MAY BE OPENED AT, because OAuth matches `redirect_uri`
        // exactly and has no wildcard. `localhost:5050` and `192.168.0.104:5050` are two registrations
        // of one client; the interface derives its own from wherever it was loaded, and an address
        // missing here is refused before anybody sees a login form.
        registeredUris(client.redirectUris()).forEach(registeredClientBuilder::redirectUri);

        // ⚠️ Optional, because not every client is a browser app that can be logged out of. An MCP
        // client holds a token and has no session to end — and `postLogoutRedirectUri(null)` throws,
        // so the absence has to be handled rather than passed through.
        registeredUris(client.postLogoutRedirectUris()).forEach(registeredClientBuilder::postLogoutRedirectUri);

        if (client.publicClient()) {
            registeredClientBuilder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            registeredClientBuilder
                .clientSecret(passwordEncoder.encode(client.clientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }

        return registeredClientBuilder.build();
    }

    /**
     * The configured addresses that are actually usable — absent, empty and blank all mean "none".
     *
     * <p>⚠️ Handled here rather than in a compact constructor on {@code ClientProperties}: javac drops
     * a record's generic component signatures once one is declared, and the properties binder then
     * sees a bare {@code List} it cannot fill with strings.
     */
    private static List<String> registeredUris(List<String> configured) {
        if (configured == null) {
            return List.of();
        }
        return configured.stream()
            .filter(uri -> uri != null && !uri.isBlank())
            .map(String::trim)
            .toList();
    }

    /**
     * Scoped to public clients only — a confidential server-side client (innoventa today) never
     * makes a cross-origin browser request against this service, so it has no business in this list.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = new ArrayList<>(identityProperties.clients().values().stream()
                .filter(IdentityProperties.ClientProperties::publicClient)
                .flatMap(client -> registeredUris(client.redirectUris()).stream())
                .map(URI::create)
                // ⚠️ A loopback redirect is a NATIVE client, not a browser one — an MCP client opens the
                // system browser and listens on 127.0.0.1 for the code. It makes no cross-origin request
                // to this service ever, so adding its host here would widen CORS for a page served from
                // 127.0.0.1 in exchange for nothing.
                .filter(redirectUri -> !isLoopback(redirectUri))
                .map(redirectUri -> redirectUri.getScheme() + "://" + redirectUri.getAuthority())
                .distinct()
                .toList());

        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(allowedOrigins);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
        return corsConfigurationSource;
    }

    /**
     * Whether a redirect goes back to the machine the client is running on.
     *
     * <p>⚠️ <strong>{@code 127.0.0.1} and {@code [::1]}, never {@code localhost}</strong> — and that is
     * not pedantry, it is what Spring Authorization Server itself matches on. RFC 8252 §7.3 says a
     * native client must use the literal address because {@code localhost} can be redirected by a hosts
     * file, and the server's own port-agnostic loopback matching follows it. A registration written
     * {@code http://localhost/callback} would be compared literally and never match the ephemeral port
     * the client actually listened on.
     */
    private static boolean isLoopback(URI redirectUri) {
        return "127.0.0.1".equals(redirectUri.getHost()) || "[::1]".equals(redirectUri.getHost());
    }

    /**
     * Stamps each issued <em>access</em> token with the requesting client's audience (set as a
     * custom client setting above), so a token minted for one app can never be replayed against
     * another. Deliberately scoped to access tokens only — an ID token's {@code aud} claim must
     * stay the client's own {@code client_id} per OIDC Core 1.0 §2 (the default Spring
     * Authorization Server sets); overwriting it broke two things discovered by actually driving a
     * full login+logout flow, not just checking that sign-in worked: (1) OIDC RP-Initiated Logout's
     * {@code id_token_hint} validation, which rejected every logout attempt with an
     * {@code invalid_token ... aud} error since {@code /connect/logout} checks the hint's audience
     * against the registered client, and (2) any spec-compliant OIDC client library's own
     * ID-token-audience validation on sign-in (required by OIDC Core 1.0 §3.1.3.7).
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            Object audience = context.getRegisteredClient().getClientSettings().getSetting("audience");
            if (audience instanceof String audienceValue) {
                context.getClaims().audience(List.of(audienceValue));
            }
        };
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsaKey();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    /**
     * ⚠️ <strong>{@code @Primary} since the protocol endpoint grew a decoder of its own.</strong>
     *
     * <p>There are two {@link JwtDecoder} beans now — this one, RS256 over the JWKS every product
     * trusts, and {@code mcpJwtDecoder}, HS256 over a secret only this service holds. Without a primary
     * the context refuses to start, because the authorization-server chain asks for one by type.
     *
     * <p>The <em>other</em> failure this prevents is the interesting one: whichever decoder wins an
     * unqualified injection decides what the protocol endpoint accepts, and getting it backwards is
     * silent — the endpoint would accept exactly the token it exists to refuse and refuse the
     * credential it exists to accept. This one is primary because every consumer except that chain
     * means it; that chain says {@code @Qualifier("mcpJwtDecoder")} and must keep saying it.
     */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * ⚠️ <strong>Unpinned by default, so this server describes itself at whatever address it was
     * reached.</strong> Spring Authorization Server derives the issuer from the current request when
     * none is configured, which is what lets one instance serve {@code http://localhost:9090} and
     * {@code http://192.168.0.104:9090} without a build, a profile or a restart between them. Pinned,
     * the discovery document reached over the network still announces {@code localhost}, and
     * {@code oidc-client-ts} refuses it for an issuer mismatch — an error that names neither the
     * address nor this setting.
     *
     * <p>⚠️ <strong>{@code identity.issuer} does NOT go away.</strong> It stays the one canonical
     * public address, and Google's and GitHub's callbacks are still built from it — those are
     * registered with a third party and cannot follow whoever is asking.
     *
     * <p>Set {@code identity.pin-issuer: true} where the address is fixed and should be asserted
     * rather than inferred: behind a reverse proxy that rewrites the Host, or anywhere a token's
     * {@code iss} is checked against a constant. ⚠️ Nothing in this workspace checks it today —
     * every resource server validates the signature and the audience, not the issuer.
     */
    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        AuthorizationServerSettings.Builder settings = AuthorizationServerSettings.builder();
        if (identityProperties.pinIssuer()) {
            settings.issuer(identityProperties.issuer());
        }
        return settings.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static RSAKey generateRsaKey() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        return new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
