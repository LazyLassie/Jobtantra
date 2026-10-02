ALTER TABLE job_executions
    DROP CONSTRAINT ck_job_executions_attempt;

ALTER TABLE job_executions
    DROP COLUMN attempt_number;
