SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000002  identity_users.role — gates Identity's own admin user-management
--  panel. Not a per-app permission (those stay local to each trusting app).
-- =============================================================================

ALTER TABLE identity_users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'ADMIN'));

UPDATE identity_users SET role = 'ADMIN' WHERE id = 'SU';
