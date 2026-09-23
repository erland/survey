ALTER TABLE admin_user
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE admin_password_token (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    created_by_admin_user_id UUID NULL REFERENCES admin_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_admin_password_token_purpose
        CHECK (purpose IN ('INITIAL_PASSWORD','PASSWORD_RESET'))
);

CREATE INDEX idx_admin_password_token_active
    ON admin_password_token (admin_user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
