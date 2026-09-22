DROP TRIGGER IF EXISTS trg_assign_default_survey_account ON survey;
DROP FUNCTION IF EXISTS assign_default_survey_account();

ALTER TABLE survey
    DROP CONSTRAINT IF EXISTS survey_owner_id_fkey;

DROP INDEX IF EXISTS idx_survey_owner;

ALTER TABLE survey
    DROP COLUMN owner_id;

ALTER TABLE survey_run
    DROP CONSTRAINT IF EXISTS survey_run_created_by_fkey;

ALTER TABLE survey_run
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE survey_run
    ADD CONSTRAINT survey_run_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL;

ALTER TABLE presentation_token
    DROP CONSTRAINT IF EXISTS presentation_token_created_by_fkey;

ALTER TABLE presentation_token
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE presentation_token
    ADD CONSTRAINT presentation_token_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES admin_user(id) ON DELETE SET NULL;
