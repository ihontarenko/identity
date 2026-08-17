SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000004  mcp_credentials — one row per client connected to the protocol
--            endpoint, and the only thing that makes a self-contained token
--            revocable before it expires.
--
--  ⚠️ THE TOKEN IS SIGNED WITH A SECRET ONLY IDENTITY HOLDS, and deliberately
--  NOT with the RSA key every other product trusts through JWKS. That is the
--  confinement: a protocol credential presented anywhere else is not a token
--  that fails a check, it is a signature that does not verify. Identity being
--  the authorization server makes this look backwards and is exactly why it is
--  right — a credential minted for one endpoint must not be indistinguishable
--  from the ones this service issues for everybody else.
--
--  ⚠️ `id` IS THE `cid` CLAIM. Every protocol call carries it, and every call
--  looks the row up — which is what lets a connection be ended in one click
--  rather than waited out for a month.
--
--  ⚠️ NO FOREIGN KEY TO identity_users, on purpose. The row has to outlive a
--  deleted account long enough to be visible and revocable rather than
--  vanishing silently; `admit` refuses a credential whose subject no longer
--  exists, which is the same answer arrived at where somebody can read it.
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX index_mcp_credentials_subject ON mcp_credentials (subject_id);
