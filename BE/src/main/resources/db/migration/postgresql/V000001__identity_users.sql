-- =============================================================================
--  V000001  Identity users — the single source of truth for "who is this user"
--
--  Universal SQL: MySQL / PostgreSQL compatible.
--  No database-specific types. updated_at maintained by the application layer
--  (@PreUpdate), not triggers.
-- =============================================================================

CREATE TABLE identity_users
(
    id              VARCHAR(36)  NOT NULL,
    provider        VARCHAR(32)  NOT NULL DEFAULT 'LOCAL'
                        CHECK (provider IN ('LOCAL', 'GOOGLE', 'GITHUB')),
    email           VARCHAR(128) NOT NULL,
    display_name    VARCHAR(255),
    avatar_url      VARCHAR(512),
    hashed_password VARCHAR(256),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,

    CONSTRAINT identity_users_pk                    PRIMARY KEY (id),
    CONSTRAINT identity_users_unique_email_provider UNIQUE (email, provider)
);

CREATE INDEX index_identity_users_email          ON identity_users (email);
CREATE INDEX index_identity_users_email_provider ON identity_users (email, provider);

-- Default admin — password: "admin" (bcrypt, default BCryptPasswordEncoder strength 10).
-- Change after first login. id='SU' is also this user's OAuth2/OIDC subject (see
-- IdentityUserDetailsService) — every issued token's `sub` claim is the row id, not the email,
-- since the same email can have separate LOCAL/GOOGLE/GITHUB rows (mirrors Innoventa's
-- security_users design) and only the id is guaranteed unambiguous.
INSERT INTO identity_users (id, provider, email, display_name, hashed_password, enabled, created_at, updated_at)
VALUES (
    'SU', 'LOCAL', 'ihontarenko@gmail.com', 'Administrator',
    '$2a$10$7ojcD.tus95PHgHHs0Jcte0L8wiogdPixHztdGTRPpgKxSRRECN0u',
    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
