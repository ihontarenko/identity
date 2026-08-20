# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Centralized identity: a Spring Authorization Server that answers "who is this user" for both
`Innoventa` and `Moneta` (formerly FinanceMonitor). It mints OAuth2 / OIDC tokens; those two apps
become **Resource Servers** that only validate tokens against this service's JWKS endpoint — never
mint their own.
The reasoning is recorded in `../Moneta/QUESTIONS.md`.

**Identity is centralized here; authorization is not.** This service knows email/password and issues
tokens. Roles, permissions, and workspace/persona scoping stay local to each app.

## Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run (MySQL, the no-flag default) — listens on port 9090
mvn spring-boot:run

# Run against PostgreSQL instead
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
```

MySQL must be reachable before startup — `docker compose up -d mysql` from the workspace root
provisions the `identity` database/user. There is no embedded-database fallback: H2 was removed
because an H2 file running `MODE=PostgreSQL` made local dev green against a dialect nothing is ever
deployed on.

JWKS is published at `/oauth2/jwks` (Spring Authorization Server's default) and OIDC discovery at
`/.well-known/openid-configuration`. Swagger UI is at `/swagger-ui.html`.

Default seeded user: `ihontarenko@gmail.com` / `admin` — change after first login.

## Key Technologies

- **Spring Boot 3.3.2 / Java 21 / Maven**
- **Spring Authorization Server** — OAuth2 / OIDC, RS256-signed JWTs, JWKS
- **Spring Security** — form login for the authorization endpoint's user-facing pages
- **Spring Data JPA + Hibernate + Flyway** — `identity_users` is the only table so far
- **Databases**: MySQL (default), PostgreSQL (opt-in `postgresql` profile) — same two-dialect
  pattern as every other product here

## Architecture

`config/SecurityConfiguration` wires everything:

- `authorizationServerSecurityFilterChain` (`@Order(1)`) — the OAuth2/OIDC endpoints
  (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, …)
- `defaultSecurityFilterChain` (`@Order(2)`) — form login for everything else
- `registeredClientRepository` — builds one `RegisteredClient` per entry under `identity.clients.*`
  in `application.yml` (currently `innoventa` and `moneta`), each with its own client
  id/redirect URI and an `audience` custom client setting. `SecurityConfiguration.buildRegisteredClient`
  branches on each entry's `public-client` flag: `innoventa` (`public-client: false`, unchanged) is a
  confidential client — a secret exchanged via `CLIENT_SECRET_BASIC`, no PKCE. `moneta`
  (`public-client: true`) is a public client — no secret (a browser SPA has nowhere safe to keep
  one), `ClientAuthenticationMethod.NONE`, PKCE required via `requireProofKey`. Don't flip
  `innoventa`'s flag without being asked; the two apps are on deliberately different auth patterns
  until Innoventa actually migrates onto this service.
- `corsConfigurationSource` — allows cross-origin requests only from `public-client` origins (derived
  from their `redirect-uri`), applied via `.cors(Customizer.withDefaults())` on **both** security
  filter chains. This had to be on both: the authorization-server chain's per-endpoint matchers are
  method-specific, so a browser's `OPTIONS` preflight to e.g. `/oauth2/token` doesn't match them and
  falls through to the default chain instead.
- `tokenCustomizer` — reads that `audience` setting and stamps it onto every issued **access**
  token's `aud` claim, so a token minted for one app cannot be replayed against the other.
  Deliberately does **not** touch ID tokens — it used to apply to both, which silently broke OIDC
  RP-Initiated Logout (`/connect/logout` rejected every `id_token_hint` with an `invalid_token ...
  aud` error, since the endpoint validates the hint's audience against the registered client) and
  violated OIDC Core 1.0 §3.1.3.7 (an ID token's `aud` must be the client's own `client_id`). Found
  by actually driving a full login → logout → re-authorize cycle, not by checking that sign-in
  worked — sign-in looked completely fine with the broken customizer.
- `jwkSource` / `jwtDecoder` — an RSA key pair generated at startup

**`authorizationServerSecurityFilterChain` needs an explicit
`.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())` call — without it, the
whole browser login flow silently breaks, and not in an obvious way.** `securityMatcher(...)` alone
correctly routes `/oauth2/authorize` into this chain (`FilterChainProxy` logs "Secured GET
/oauth2/authorize"), but `OAuth2AuthorizationEndpointFilter` itself then never actually processes the
request — it falls through to Spring MVC's static-resource handler and 404s as
`NoResourceFoundException: No static resource oauth2/authorize`, which Boot's error handling silently
forwards to `/error`. Since that forward requires auth too, `ExceptionTranslationFilter` still
redirects to `/login` — so a curl/browser test that only checks "did I land on the login page" sees a
perfectly clean 302 and looks completely successful. The bug only surfaces on the *next* hop: after a
real login, `SavedRequestAwareAuthenticationSuccessHandler` replays the saved request — which is
`/error?...&continue`, not the original `/oauth2/authorize?...` — landing on a Whitelabel 404 instead
of back at the SPA. Confirmed this reproduces identically even on a version of this file with zero
CORS-related code at all, so it isn't a CORS interaction; it seems to be a genuine requirement of
Spring Authorization Server's exception-translation/request-cache machinery that isn't obvious from
the reference docs' code shape. Found only by driving the *entire* flow (redirect → real login submit
→ replay) in an actual browser and reading `HttpSessionRequestCache`'s "Saved request" log line, not
by checking the first redirect in isolation.

**The RSA key pair is regenerated on every restart.** That's fine for local development (existing
tokens just stop verifying) but is the first thing to fix before this runs anywhere shared — replace
`SecurityConfiguration.generateRsaKey()` with a key loaded from a persisted keystore or secret store,
since every resource server's trust in this service rests on that key never silently rotating.

`security/IdentityUserDetailsService` loads `identity_users` rows (via `IdentityUserRepository`,
backed by the `domain/IdentityUser` entity) and grants a single generic `ROLE_USER` authority — this
service does not model per-app roles.

## Database Migrations

Same convention as `Innoventa/BE`: Flyway only, `ddl-auto: validate`, one file per version under
`src/main/resources/db/migration/{mysql,postgresql}/`, named `V{6-digit}__{description}.sql`.
Write migrations compatible with both dialects (`CHECK` constraints instead of `ENUM`, no
dialect-specific syntax outside the per-dialect `ENGINE=...`/charset lines already isolated in the
`mysql` folder).

## Client Configuration

Each trusting app is one block under `identity.clients` in `application.yml`:

```yaml
identity:
  clients:
    innoventa:
      client-id: innoventa-web
      client-secret: ...
      redirect-uris: http://localhost:5010/login/oauth2/code/identity
      post-logout-redirect-uris: http://localhost:5010
      audience: innoventa
      public-client: false
    tessera:
      client-id: tessera-web
      # ⚠️ PLURAL, AND ORDER MATTERS. OAuth matches `redirect_uri` exactly and has no wildcard, so
      # every address an interface may be opened at is its own registration — a phone reaching the
      # Vite server over the LAN sends `192.168.0.104:5050`, which is a different client as far as
      # this server is concerned. The first entry is the canonical one: `ApplicationLinksController`
      # links to it. Comma-separated binds the same as a YAML sequence, so one environment variable
      # still configures several.
      redirect-uris: >
        http://localhost:5050/login/oauth2/code/identity,
        http://192.168.0.104:5050/login/oauth2/code/identity
      post-logout-redirect-uris: http://localhost:5050,http://192.168.0.104:5050
      audience: tessera
      public-client: true   # no client-secret — PKCE replaces it, see Architecture above
```

**`identity.pin-issuer` is `false`, and that is what makes more than one address work at all.** Spring
Authorization Server derives the issuer from the current request when none is configured, so the
discovery document fetched at `192.168.0.104:9090` announces that address rather than `localhost` —
which is what `oidc-client-ts` compares its `authority` against before it will proceed. Pinned, the
LAN case fails with an issuer mismatch that names neither the address nor the setting. `identity.issuer`
is still a concrete value and still required: Google's and GitHub's callbacks are built from it and are
registered with a third party, so they cannot follow whoever is asking.

`post-logout-redirect-uri` is required for OIDC RP-Initiated Logout (`/connect/logout`, Spring
Authorization Server's default end-session endpoint — enabled automatically) to actually redirect
back to the app after signing out; without a matching registered URI, the server won't redirect
there. Moneta's sign-out button calls `auth.signoutRedirect()` (not `auth.removeUser()`,
which only clears the local token and left Identity's session cookie alive — `ProtectedRoute` would
then immediately re-trigger `signinRedirect()`, and since the session was never actually terminated,
Identity silently re-authenticated and bounced the user right back in without ever showing a login
prompt).

Client secrets and redirect URIs are overridable via `IDENTITY_<NAME>_CLIENT_ID` /
`IDENTITY_<NAME>_CLIENT_SECRET` / `IDENTITY_<NAME>_REDIRECT_URIS` (comma-separated) environment variables — never commit
real secrets to `application.yml`. `public-client` entries have no secret to override.

## Web UI (Login + Account)

Identity has a small hand-rolled Thymeleaf UI now — its first frontend of any kind (`spring-boot-
starter-thymeleaf` added to `pom.xml`; no Node/build tooling, matching Innoventa's own hand-rolled-
CSS approach rather than introducing Tailwind into a Maven project):

- `web/LoginViewController` (`GET /login`) replaces Spring Security's default login page —
  `templates/login.html`, wired via `SecurityConfiguration.defaultSecurityFilterChain`'s
  `.formLogin(form -> form.loginPage("/login").permitAll())`.
- `web/AccountController` (`GET /account`, `POST /account/profile`, `POST /account/password`) —
  self-service for an already-authenticated user: edit display name, change password (current +
  new + confirm, current-password verified via the existing `PasswordEncoder` bean). Backed by
  `service/AccountService`, deliberately kept free of any HTTP/view concerns (no `Model`, no
  `BindingResult`) — this is the layer a future REST API sits on top of, not `AccountController`
  itself. Form-backing types (`web/form/ProfileForm`, `PasswordForm`) are plain validated records
  bound via `@ModelAttribute`, not MapStruct DTOs — that convention exists to decouple JSON API
  shapes from JPA entities across a serialization boundary; a same-request Thymeleaf form binding
  has no such boundary to justify the extra layer.
- `templates/fragments/brand.html` — shared `<head>` (fonts, `static/css/identity.css` link) and
  brand-mark fragment, `th:replace`d into both pages.
- `static/css/identity.css` — palette values ported from `Innoventa/UI/src/stores/themeStore.ts`'s
  `cream` (light, default) and `steel` (dark, via `prefers-color-scheme` only — no toggle, no full
  27-theme picker; deliberately smaller scope than Innoventa/Moneta's interactive theme
  systems since this is a page users pass through briefly, not live in).
- CSRF: Thymeleaf's Spring integration (part of `spring-boot-starter-thymeleaf`, not the separate
  `thymeleaf-extras-springsecurity6` module) auto-injects the CSRF hidden field into any `th:action`
  form when a `RequestDataValueProcessor` bean is present — Spring Security's CSRF configuration
  registers one automatically. Do **not** add a manual `<input type="hidden" th:name="${_csrf...}"`
  field — it renders twice (learned by testing this against a real browser flow, not just checking
  the page loads).

**Future migration path (Innoventa onto centralized identity):** invites, space membership, and
roles stay entirely in Innoventa's own domain forever — per "Identity is centralized; authorization
is not" above, those are authorization concerns, not identity ones. When Innoventa migrates, it
becomes a pure Resource Server (like Moneta already is) and keeps rendering its own profile
page UI; it doesn't redirect to Identity's pages for that. The reason `AccountService` has no
HTTP-layer leakage is exactly so Innoventa's backend (or frontend) can call the same operations via
a future `AccountRestController` (JSON, bearer-token authenticated) without any rework to the
service layer — that REST controller doesn't exist yet and isn't needed until that migration
actually starts.

**Planned, not yet built: Identity's own React frontend.** The Thymeleaf pages above are explicitly
a placeholder — the plan is to replace them with a React SPA (landing page, account management, an
admin user-management panel with block/unlock/delete, MFA), matching Innoventa/Moneta's
stack, "get rid of spring rendering" entirely. Not started; needs its own scoping pass before
implementation (bundles several independently-large features — don't build it as one undifferentiated
blob).

## Google / GitHub Login

Mirrors Innoventa's `security/oauth2` design exactly (same `Provider` enum values, same
extract-email/extract-display-name logic, same lookup-by-`(email, provider)` upsert shape) — see
`Innoventa/BE/src/main/java/net/innoventa/security/oauth2/` for the reference this was ported from.
What's necessarily different: Innoventa is a stateless JWT backend (issues a one-time code, redirects
to a separate SPA to exchange it); Identity's human-facing chain is session-based, so
`OAuth2LoginSuccessHandler` swaps the post-login `SecurityContext` to a session-persisted
`UsernamePasswordAuthenticationToken` and delegates to `SavedRequestAwareAuthenticationSuccessHandler`
instead — see that class's Javadoc for the full reasoning.

**`identity_users.provider` (`LOCAL`/`GOOGLE`/`GITHUB`)** — added directly to `V000001`'s `CREATE
TABLE` rather than a follow-up migration (this table hadn't shipped anywhere beyond local dev yet).
The same email can now have separate rows per provider — same design as Innoventa's
`security_users`, same tradeoff: no account linking, signing in via a second provider with an
already-registered email creates a *second*, separate identity row, not one identity with two
sign-in methods. Accepted as a known gap, matching Innoventa's own existing risk tolerance, not
silently fixed.

**Every issued token's `sub` claim is the row `id`, not the email** — this had to change together
with the provider column: once the same email can be ambiguous, only the id is guaranteed unique.
`IdentityUserDetailsService.loadUserByUsername` still takes email as its parameter (what the user
types to log in, scoped to `Provider.LOCAL` only) but returns `UserDetails` built with the row's
`id` as username; `OAuth2LoginSuccessHandler`'s session token-swap does the same. This was a
breaking change for the one real consumer that existed — Moneta's `workspaces.owner_subject`
was keyed by the old email-shaped subject; the existing row was migrated in-place (`UPDATE
workspaces SET owner_subject = 'SU' WHERE owner_subject = 'ihontarenko@gmail.com'`, run directly
against the dev MySQL container, not a new Moneta migration file) rather than left orphaned.

**Credentials**: `application-sensitive.yml` (gitignored) reuses Innoventa's actual Google/GitHub
OAuth app client-id/secret verbatim, copied by explicit instruction rather than provisioning new
apps. This only actually works once Identity's own callback URLs are registered on those same apps:
- **Google** supports multiple authorized redirect URIs on one OAuth client — add
  `http://localhost:9090/login/oauth2/code/google` (plus the prod equivalent) alongside Innoventa's
  existing one in Google Cloud Console → Credentials → this OAuth client. Non-breaking for Innoventa.
- **GitHub OAuth Apps only support ONE callback URL each** — Innoventa's existing GitHub app cannot
  simultaneously serve both `http://innoventa.net/login/oauth2/code/github` and
  `http://localhost:9090/login/oauth2/code/github`. Either create a second GitHub OAuth App for
  Identity (new client-id/secret, update `application-sensitive.yml`) or repoint the existing app's
  callback URL (breaks Innoventa's GitHub login until it's updated too). **Not yet resolved — GitHub
  login will fail with a redirect_uri mismatch until one of these is done manually in GitHub's
  Developer Settings.**

## ⚠️ No Dynamic Client Registration — And What It Cost

This service has **no RFC 7591 registration endpoint**, and its metadata therefore advertises no
`registration_endpoint`. That is fine for every browser client (each is a block under
`identity.clients`) and fatal for one kind of caller: **a Model Context Protocol client, which has
nowhere to be told a client id.** It reads the authorization server's metadata, registers itself, and
absent that endpoint refuses to continue — Claude Code stops with `Incompatible auth server: does not
support dynamic client registration` *after* every discovery step has succeeded, which makes it look
like a Tessera problem rather than this one.

The `tessera-mcp` client under `identity.clients` was written for exactly that caller and could never be
reached by it. It is left in place with the reasoning in a comment beside it, and **Tessera now issues its
own credential for its own protocol endpoint** — confined to that endpoint, with the person still
authenticated here (the consent screen sits behind an Identity session). See
`Tessera/docs/adr/0020-tessera-issues-its-own-protocol-credential.md`.

If a second product ever serves the protocol, that is the point to reconsider: a registration endpoint
here, with the audience resolved from the `resource` parameter (RFC 8707) rather than from the client,
would serve all of them — at the cost of an installation-wide authorization server that accepts anonymous
client registrations.

## Open Items

Scaffolded but not yet built:

- No registration / password-reset / email-verification flow yet — only a seeded admin user exists
  (self-service account management for an *already-authenticated* user now exists — see "Web UI"
  above — but there's still no path for an anonymous person to create an account or recover access).
- No REST API for account operations yet (see "Web UI" above) — add `AccountRestController` when
  Innoventa's migration onto this service actually starts, not before.
- No persisted `OAuth2Authorization`/`OAuth2AuthorizationConsent` store (registered-client repo is
  in-memory) — session/consent management UI isn't buildable until this exists.
- No refresh-token revocation store (Innoventa's current local auth has one in Redis; this service
  doesn't yet).
- Persisted signing key (see above) — required before Innoventa or Moneta point at this
  service for real.
- Client secrets in `application.yml` are dev defaults; production needs real generated secrets
  supplied via environment variables.
