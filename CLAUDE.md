# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Centralized identity: a Spring Authorization Server that answers "who is this user" for both
`Innoventa` and `FinanceMonitor`. It mints OAuth2 / OIDC tokens; those two apps become **Resource
Servers** that only validate tokens against this service's JWKS endpoint — never mint their own.
The reasoning is recorded in `../FinanceMonitor/QUESTIONS.md`.

**Identity is centralized here; authorization is not.** This service knows email/password and issues
tokens. Roles, permissions, and workspace/persona scoping stay local to each app.

## Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run (H2 dev profile, default) — listens on port 9090
mvn spring-boot:run

# Run with MySQL / PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
```

JWKS is published at `/oauth2/jwks` (Spring Authorization Server's default) and OIDC discovery at
`/.well-known/openid-configuration`. Swagger UI is at `/swagger-ui.html`. H2 console (dev only) is at
`/h2-console`.

Default seeded user: `ihontarenko@gmail.com` / `admin` — change after first login.

## Key Technologies

- **Spring Boot 3.3.2 / Java 21 / Maven**
- **Spring Authorization Server** — OAuth2 / OIDC, RS256-signed JWTs, JWKS
- **Spring Security** — form login for the authorization endpoint's user-facing pages
- **Spring Data JPA + Hibernate + Flyway** — `identity_users` is the only table so far
- **Databases**: H2 (default/dev), MySQL, PostgreSQL — same three-profile pattern as `Innoventa/BE`

## Architecture

`config/SecurityConfiguration` wires everything:

- `authorizationServerSecurityFilterChain` (`@Order(1)`) — the OAuth2/OIDC endpoints
  (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, …)
- `defaultSecurityFilterChain` (`@Order(2)`) — form login for everything else
- `registeredClientRepository` — builds one `RegisteredClient` per entry under `identity.clients.*`
  in `application.yml` (currently `innoventa` and `financemonitor`), each with its own client
  id/secret/redirect URI and an `audience` custom client setting
- `tokenCustomizer` — reads that `audience` setting and stamps it onto every issued token's `aud`
  claim, so a token minted for one app cannot be replayed against the other
- `jwkSource` / `jwtDecoder` — an RSA key pair generated at startup

**The RSA key pair is regenerated on every restart.** That's fine for local development (existing
tokens just stop verifying) but is the first thing to fix before this runs anywhere shared — replace
`SecurityConfiguration.generateRsaKey()` with a key loaded from a persisted keystore or secret store,
since every resource server's trust in this service rests on that key never silently rotating.

`security/UserDetailsServiceImpl` loads `identity_users` rows and grants a single generic
`ROLE_USER` authority — this service does not model per-app roles.

## Database Migrations

Same convention as `Innoventa/BE`: Flyway only, `ddl-auto: validate`, one file per version under
`src/main/resources/db/migration/{h2,mysql,postgresql}/`, named `V{6-digit}__{description}.sql`.
Write migrations compatible with all three dialects (`CHECK` constraints instead of `ENUM`, no
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
      redirect-uri: http://localhost:5173/login/oauth2/code/identity
      audience: innoventa
```

Client secrets and redirect URIs are overridable via `IDENTITY_<NAME>_CLIENT_ID` /
`IDENTITY_<NAME>_CLIENT_SECRET` / `IDENTITY_<NAME>_REDIRECT_URI` environment variables — never commit
real secrets to `application.yml`.

## Open Items

Scaffolded but not yet built:

- No registration / password-reset / email-verification flow yet — only a seeded admin user exists.
- No refresh-token revocation store (Innoventa's current local auth has one in Redis; this service
  doesn't yet).
- Persisted signing key (see above) — required before Innoventa or FinanceMonitor point at this
  service for real.
- Client secrets in `application.yml` are dev defaults; production needs real generated secrets
  supplied via environment variables.
