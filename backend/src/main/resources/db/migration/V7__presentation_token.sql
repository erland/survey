CREATE TABLE presentation_token (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES survey_run(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_presentation_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_presentation_token_run_id ON presentation_token(run_id);
CREATE INDEX idx_presentation_token_expires_at ON presentation_token(expires_at);
