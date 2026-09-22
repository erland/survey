CREATE TABLE participant_session (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES survey_run(id) ON DELETE CASCADE,
    client_token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_participant_session_token UNIQUE (run_id, client_token_hash),
    CONSTRAINT chk_participant_session_status CHECK (status IN ('ACTIVE','SUBMITTED','EXPIRED'))
);

CREATE INDEX idx_participant_session_run ON participant_session(run_id);
CREATE INDEX idx_participant_session_last_activity ON participant_session(run_id, last_activity_at);
