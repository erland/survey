CREATE TABLE survey_run (
    id UUID PRIMARY KEY,
    survey_id UUID NOT NULL REFERENCES survey(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES admin_user(id) ON DELETE RESTRICT,
    public_id VARCHAR(64) NOT NULL,
    join_code VARCHAR(12) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    opens_at TIMESTAMPTZ NULL,
    closes_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMPTZ NULL,
    closed_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_survey_run_public_id UNIQUE (public_id),
    CONSTRAINT uq_survey_run_join_code UNIQUE (join_code),
    CONSTRAINT chk_survey_run_status CHECK (status IN ('DRAFT','SCHEDULED','OPEN','CLOSED')),
    CONSTRAINT chk_survey_run_window CHECK (opens_at IS NULL OR closes_at IS NULL OR opens_at < closes_at)
);

CREATE TABLE survey_run_question (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES survey_run(id) ON DELETE CASCADE,
    source_question_id UUID NULL,
    position INTEGER NOT NULL,
    type VARCHAR(32) NOT NULL,
    text TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    scale_min INTEGER NULL,
    scale_max INTEGER NULL,
    scale_min_label VARCHAR(200) NULL,
    scale_max_label VARCHAR(200) NULL,
    CONSTRAINT uq_survey_run_question_position UNIQUE (run_id, position),
    CONSTRAINT chk_run_question_position CHECK (position >= 0),
    CONSTRAINT chk_run_question_type CHECK (type IN ('TEXT','YES_NO','SINGLE_CHOICE','MULTIPLE_CHOICE','SCALE')),
    CONSTRAINT chk_run_scale_bounds CHECK (
      type <> 'SCALE' OR (scale_min IS NOT NULL AND scale_max IS NOT NULL AND scale_min < scale_max)
    )
);

CREATE TABLE survey_run_option (
    id UUID PRIMARY KEY,
    run_question_id UUID NOT NULL REFERENCES survey_run_question(id) ON DELETE CASCADE,
    source_option_id UUID NULL,
    position INTEGER NOT NULL,
    value VARCHAR(200) NOT NULL,
    label VARCHAR(500) NOT NULL,
    CONSTRAINT uq_survey_run_option_position UNIQUE (run_question_id, position),
    CONSTRAINT uq_survey_run_option_value UNIQUE (run_question_id, value),
    CONSTRAINT chk_run_option_position CHECK (position >= 0)
);

CREATE INDEX idx_survey_run_survey ON survey_run(survey_id);
CREATE INDEX idx_survey_run_created_by ON survey_run(created_by);
CREATE INDEX idx_survey_run_question_run ON survey_run_question(run_id);
CREATE INDEX idx_survey_run_option_question ON survey_run_option(run_question_id);
