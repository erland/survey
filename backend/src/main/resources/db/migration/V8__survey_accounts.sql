ALTER TABLE admin_user
    ADD COLUMN system_admin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE survey_account (
    id UUID PRIMARY KEY,
    name VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE survey_account_admin (
    survey_account_id UUID NOT NULL REFERENCES survey_account(id) ON DELETE CASCADE,
    admin_user_id UUID NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (survey_account_id, admin_user_id),
    CONSTRAINT chk_survey_account_admin_role CHECK (role IN ('ADMIN'))
);

CREATE INDEX idx_survey_account_admin_user
    ON survey_account_admin(admin_user_id);

ALTER TABLE survey
    ADD COLUMN survey_account_id UUID NULL REFERENCES survey_account(id),
    ADD COLUMN created_by_admin_user_id UUID NULL REFERENCES admin_user(id) ON DELETE SET NULL;

DO $$
DECLARE
    default_account_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO survey_account (id, name)
    VALUES (default_account_id, 'Default');

    INSERT INTO survey_account_admin (survey_account_id, admin_user_id, role)
    SELECT default_account_id, id, 'ADMIN'
    FROM admin_user
    ON CONFLICT DO NOTHING;

    UPDATE survey
    SET survey_account_id = default_account_id,
        created_by_admin_user_id = owner_id;
END $$;

CREATE OR REPLACE FUNCTION assign_default_survey_account()
RETURNS TRIGGER AS $
BEGIN
    IF NEW.survey_account_id IS NULL THEN
        SELECT m.survey_account_id
        INTO NEW.survey_account_id
        FROM survey_account_admin m
        WHERE m.admin_user_id = NEW.owner_id
        ORDER BY m.created_at, m.survey_account_id
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$ LANGUAGE plpgsql;

CREATE TRIGGER trg_assign_default_survey_account
BEFORE INSERT ON survey
FOR EACH ROW
WHEN (NEW.survey_account_id IS NULL)
EXECUTE FUNCTION assign_default_survey_account();

ALTER TABLE survey
    ALTER COLUMN survey_account_id SET NOT NULL;

CREATE INDEX idx_survey_account
    ON survey(survey_account_id);

CREATE INDEX idx_survey_created_by
    ON survey(created_by_admin_user_id);
