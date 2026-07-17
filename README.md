# Identity

Centralized OAuth2 / OpenID Connect authorization server for the Innoventa ecosystem. It answers
one question — "who is this user" — for both `Innoventa` and `FinanceMonitor`. Those two applications
never mint their own tokens; they validate tokens issued here against this service's public keys.

This is `id.innoventa.net` in local-development form: a standalone Spring Boot service, not yet wired
into either consuming application.

## Requirements

- Java 21
- Maven 3.9+
- Nothing else for the default profile — it runs on an embedded H2 file database. MySQL/PostgreSQL
  are supported via profiles (see below) but not required for local development.

## Quick Start

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

The service starts on **http://localhost:9090**. Confirm it is up:

```bash
curl http://localhost:9090/actuator/health
curl http://localhost:9090/.well-known/openid-configuration
curl http://localhost:9090/oauth2/jwks
```

A default admin user is seeded by the first migration:

| Email | Password |
|---|---|
| `ihontarenko@gmail.com` | `admin` |

Change or remove this before the service is reachable from anywhere but your own machine.

## Running Against MySQL or PostgreSQL

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
```

Each profile expects a database named `identity` and reads connection details from
`IDENTITY_DB_URL` / `IDENTITY_DB_USERNAME` / `IDENTITY_DB_PASSWORD` (see `application.yml` for
defaults). Flyway applies the matching migration set under `db/migration/{mysql,postgresql}/`
automatically on startup.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `/.well-known/openid-configuration` | OIDC discovery document |
| `/oauth2/jwks` | Public keys (JWKS) — this is what resource servers point their `jwk-set-uri` at |
| `/oauth2/authorize` | Authorization endpoint (redirect target for the login flow) |
| `/oauth2/token` | Token endpoint (authorization code / refresh token exchange) |
| `/userinfo` | OIDC user info |
| `/connect/logout` | OIDC end-session endpoint |
| `/login` | Form login page for the authorization endpoint's user-facing step |
| `/swagger-ui.html` | OpenAPI UI (currently empty — no REST controllers of its own yet) |
| `/h2-console` | H2 database console (dev profile only) |
| `/actuator/health` | Health check |

## Registered Clients

Trusting applications are configured under `identity.clients` in `application.yml`. Two are seeded
for local development:

```yaml
identity:
  issuer: http://localhost:9090
  clients:
    innoventa:
      client-id: innoventa-web
      client-secret: innoventa-dev-secret
      redirect-uri: http://localhost:5173/login/oauth2/code/identity
      audience: innoventa
    financemonitor:
      client-id: financemonitor-web
      client-secret: financemonitor-dev-secret
      redirect-uri: http://localhost:5174/login/oauth2/code/identity
      audience: financemonitor
```

Every token this service issues carries an `aud` claim matching the requesting client's `audience`
setting, so a token minted for `innoventa` is rejected by a resource server checking for
`financemonitor`, and vice versa.

Override any of these per environment with `IDENTITY_INNOVENTA_CLIENT_ID`,
`IDENTITY_INNOVENTA_CLIENT_SECRET`, `IDENTITY_INNOVENTA_REDIRECT_URI` (and the `FINANCEMONITOR`
equivalents) — never commit real secrets into `application.yml`.

## Trying the Authorization Code Flow Manually

With the service running, open this URL in a browser (adjust `redirect_uri` to match the client you
want to test):

```
http://localhost:9090/oauth2/authorize?
  response_type=code&
  client_id=innoventa-web&
  scope=openid%20profile%20email&
  redirect_uri=http://localhost:5173/login/oauth2/code/identity&
  code_challenge=<PKCE_CHALLENGE>&
  code_challenge_method=S256
```

Log in as the seeded admin user. The browser redirects to `redirect_uri` with a `?code=...` — since
no application is listening there yet, the browser will show a connection error; the code is still
valid and can be exchanged manually:

```bash
curl -u innoventa-web:innoventa-dev-secret \
  -d grant_type=authorization_code \
  -d code=<CODE_FROM_REDIRECT> \
  -d redirect_uri=http://localhost:5173/login/oauth2/code/identity \
  -d code_verifier=<PKCE_VERIFIER> \
  http://localhost:9090/oauth2/token
```

This returns an `access_token` (a signed JWT) and, if the `refresh_token` scope was granted, a
`refresh_token`. Decode the access token against `/oauth2/jwks` to confirm the `aud` claim matches
the client you authenticated as.

## Known Limitations (Read Before Pointing a Real App at This)

- **The RSA signing key is regenerated on every restart.** Every token issued before a restart stops
  verifying the moment it happens. This is fine for local development but must be replaced with a
  persisted key (keystore or secret manager) before Innoventa or FinanceMonitor depend on this
  service for real — see `SecurityConfiguration.generateRsaKey()`.
- **No self-service registration, password reset, or email verification yet.** Only the seeded admin
  user exists.
- **No refresh-token revocation store.** A leaked refresh token cannot currently be invalidated
  before it expires.
- **Client secrets in `application.yml` are development placeholders.**

## Further Reading

`CLAUDE.md` in this directory documents the internal wiring (`SecurityConfiguration`, token
customization, migration conventions) for anyone extending this service. The decision to centralize
identity this way — and why authorization stays local to each app — is recorded in
`../FinanceMonitor/QUESTIONS.md`.
