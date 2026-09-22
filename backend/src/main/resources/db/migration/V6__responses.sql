CREATE TABLE response (
    id UUID PRIMARY KEY,
    participant_session_id UUID NOT NULL REFERENCES participant_session(id) ON DELETE CASCADE,
    run_question_id UUID NOT NULL REFERENCES survey_run_question(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_response_session_question UNIQUE (participant_session_id, run_question_id)
);

CREATE TABLE response_value (
    id UUID PRIMARY KEY,
    response_id UUID NOT NULL REFERENCES response(id) ON DELETE CASCADE,
    text_value TEXT NULL,
    numeric_value INTEGER NULL,
    boolean_value BOOLEAN NULL,
    option_id UUID NULL REFERENCES survey_run_option(id) ON DELETE CASCADE,
    CONSTRAINT chk_response_value_one_kind CHECK (
        ((text_value IS NOT NULL)::int + (numeric_value IS NOT NULL)::int + (boolean_value IS NOT NULL)::int + (option_id IS NOT NULL)::int) = 1
    )
);

CREATE INDEX idx_response_participant ON response(participant_session_id);
CREATE INDEX idx_response_question ON response(run_question_id);
CREATE INDEX idx_response_value_response ON response_value(response_id);
