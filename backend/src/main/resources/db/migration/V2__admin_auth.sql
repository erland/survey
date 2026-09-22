CREATE TABLE admin_user (
    id UUID PRIMARY KEY,
    username VARCHAR(200) NOT NULL UNIQUE,
    password_hash VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_session (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_admin_session_active_token
    ON admin_session (token_hash, expires_at)
    WHERE revoked_at IS NULL;
