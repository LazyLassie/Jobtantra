ALTER TABLE job_executions
    ADD COLUMN available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN claim_token UUID;

UPDATE job_executions
SET lease_expires_at = updated_at
WHERE status = 'RUNNING';

CREATE INDEX idx_job_executions_available_at
    ON job_executions (status, available_at)
    WHERE status = 'QUEUED';

CREATE INDEX idx_job_executions_lease_expires_at
    ON job_executions (status, lease_expires_at)
    WHERE status = 'RUNNING';