-- =============================================================================
--  V000004  mcp_credentials — one row per client connected to the protocol
--            endpoint, and the only thing that makes a self-contained token
--            revocable before it expires.
--
--  ⚠️ THE TOKEN IS SIGNED WITH A SECRET ONLY IDENTITY HOLDS, and deliberately
--  NOT with the RSA key every other product trusts through JWKS. That is the
--  confinement: a protocol credential presented anywhere else is not a token
--  that fails a check, it is a signature that does not verify.
--
--  ⚠️ `id` IS THE `cid` CLAIM, looked up on every protocol call — which is what
--  lets a connection be ended in one click rather than waited out.
--
--  ⚠️ NO FOREIGN KEY TO identity_users, on purpose — see the MySQL twin.
-- =============================================================================

CREATE TABLE mcp_credentials
(
    id                 VARCHAR(36)  NOT NULL,
    subject_id         VARCHAR(36)  NOT NULL,
    client_id          VARCHAR(128),
    client_name        VARCHAR(255),
    refresh_token_hash VARCHAR(128) NOT NULL,
    created_at         TIMESTAMP    NOT NULL,
    last_used_at       TIMESTAMP    NULL,
    revoked_at         TIMESTAMP    NULL,

    CONSTRAINT mcp_credentials_pk PRIMARY KEY (id),
    CONSTRAINT mcp_credentials_unique_refresh UNIQUE (refresh_token_hash)
);

CREATE INDEX index_mcp_credentials_subject ON mcp_credentials (subject_id);
