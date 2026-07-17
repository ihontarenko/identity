SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000001  Identity users — the single source of truth for "who is this user"
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible.
--  No database-specific types. updated_at maintained by the application layer
--  (@PreUpdate), not triggers.
-- =============================================================================

CREATE TABLE identity_users
(
    id              VARCHAR(36)  NOT NULL,
    email           VARCHAR(128) NOT NULL,
    display_name    VARCHAR(255),
    hashed_password VARCHAR(256),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,

    CONSTRAINT identity_users_pk           PRIMARY KEY (id),
    CONSTRAINT identity_users_unique_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX index_identity_users_email ON identity_users (email);

-- Default admin — password: "admin" (bcrypt cost 12). Change after first login.
INSERT INTO identity_users (id, email, display_name, hashed_password, enabled, created_at, updated_at)
VALUES (
    'SU', 'ihontarenko@gmail.com', 'Administrator',
    '$2a$12$4Kcv//g02PK/fsPhZoZsFupDylHBqPHVunWPpcxQOELYDLBO1CM.m',
    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
