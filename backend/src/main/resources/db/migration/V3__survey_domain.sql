CREATE TABLE survey (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_survey_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);

CREATE TABLE survey_question (
    id UUID PRIMARY KEY,
    survey_id UUID NOT NULL REFERENCES survey(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    type VARCHAR(32) NOT NULL,
    text TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    scale_min INTEGER NULL,
    scale_max INTEGER NULL,
    scale_min_label VARCHAR(200) NULL,
    scale_max_label VARCHAR(200) NULL,
    CONSTRAINT uq_survey_question_position UNIQUE (survey_id, position),
    CONSTRAINT chk_question_position CHECK (position >= 0),
    CONSTRAINT chk_question_type CHECK (type IN ('TEXT','YES_NO','SINGLE_CHOICE','MULTIPLE_CHOICE','SCALE')),
    CONSTRAINT chk_scale_bounds CHECK (
      type <> 'SCALE' OR (scale_min IS NOT NULL AND scale_max IS NOT NULL AND scale_min < scale_max)
    )
);

CREATE TABLE question_option (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES survey_question(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    value VARCHAR(200) NOT NULL,
    label VARCHAR(500) NOT NULL,
    CONSTRAINT uq_question_option_position UNIQUE (question_id, position),
    CONSTRAINT uq_question_option_value UNIQUE (question_id, value),
    CONSTRAINT chk_option_position CHECK (position >= 0)
);

CREATE INDEX idx_survey_owner ON survey(owner_id);
CREATE INDEX idx_survey_question_survey ON survey_question(survey_id);
CREATE INDEX idx_question_option_question ON question_option(question_id);
