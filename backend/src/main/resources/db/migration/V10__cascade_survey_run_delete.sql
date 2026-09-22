ALTER TABLE survey_run
    DROP CONSTRAINT IF EXISTS survey_run_survey_id_fkey;

ALTER TABLE survey_run
    ADD CONSTRAINT survey_run_survey_id_fkey
    FOREIGN KEY (survey_id) REFERENCES survey(id) ON DELETE CASCADE;
