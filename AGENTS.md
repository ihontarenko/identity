# Repository Guidelines

## Project Structure & Module Organization

The Spring Boot authorization server lives in `src/main/java/net/innoventa/identity`, organized by concern: `config`, `security`, `domain`, `repository`, `service`, and `web`. Runtime configuration and static assets are in `src/main/resources`; database changes belong in matching Flyway folders under `db/migration/{h2,mysql,postgresql}`. Keep each migration present for all supported databases.

The separate React/Vite client is in `UI/`. Its application code is under `UI/src` (`api`, `components`, `context`, `pages`); production UI assets are built into `src/main/resources/static` for Spring Boot to serve. Place backend tests in `src/test/java` and frontend tests beside their feature or in `UI/src`.

## Build, Test, and Development Commands

```powershell
mvn clean package        # compile, run backend tests, create the JAR
mvn spring-boot:run      # run with the default H2 profile on port 9090
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
cd UI; npm ci            # install locked frontend dependencies
cd UI; npm run dev       # start the Vite development server
cd UI; npm run lint      # lint TypeScript/React code with oxlint
cd UI; npm run build     # type-check and produce the static frontend build
```

Use the `mysql` profile when validating MySQL-specific configuration. Do not commit generated `target/` output or transient UI build artifacts unless intentionally updating the backend-served static bundle.

## Coding Style & Naming Conventions

Use Java 21 and the existing Spring conventions: four-space indentation, package names in lowercase, PascalCase classes, and `*Controller`, `*Service`, `*Repository`, and `*Request`/`*Response` suffixes where applicable. Keep HTTP concerns in `web` and business rules in `service`.

Use TypeScript with the existing two-space indentation and PascalCase React component filenames (for example, `AccountPage.tsx`). Run `npm run lint` and `npm run build` after UI changes. Name Flyway files `V000003__short_description.sql` and keep their semantics aligned across database dialects.

## Testing Guidelines

The backend includes JUnit 5, Spring Boot Test, Spring Security Test, and Testcontainers. Add focused `*Test.java` tests for changed behavior and run `mvn test`. There is no frontend test script or stated coverage threshold yet; at minimum, lint and build the UI before submitting.

## Commit & Pull Request Guidelines

Follow the established commit pattern: `[IDENTITY][FEATURE] Add ...`, `[IDENTITY][FIX] Correct ...`, or `[IDENTITY][REFACTOR] Rename ...`. Keep commits narrowly scoped. Pull requests should explain the user or security impact, link the related issue when available, list validation commands, and include screenshots for visible UI changes. Never commit real database credentials, OAuth client secrets, or signing keys; use environment variables instead.
