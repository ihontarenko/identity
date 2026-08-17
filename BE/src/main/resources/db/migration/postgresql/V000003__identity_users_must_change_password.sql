-- =============================================================================
--  V000003  identity_users.must_change_password
--
--  An administrator can raise an account now, and the password they type is one
--  they chose for somebody else. The flag is what makes that a temporary state
--  rather than a credential the administrator keeps.
--
--  ⚠️ IT IS ENFORCED AT THE SESSION, NOT BY HIDING A SCREEN. While it is set,
--  the session reaches the change-password call and nothing else — INCLUDING
--  /oauth2/authorize. Otherwise a person holding a password their administrator
--  chose walks straight into Innoventa, Moneta, Tessera and WiQ, and the flag
--  is decoration. See PasswordChangeRequiredFilter.
--
--  ⚠️ DEFAULT FALSE, so every account that already exists is unaffected — the
--  seeded administrator included. Only an admin-raised account, and an account
--  whose password an admin has just set, arrive with it TRUE.
-- =============================================================================

ALTER TABLE identity_users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
