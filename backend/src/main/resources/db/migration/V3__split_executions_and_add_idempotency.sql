ALTER TABLE job_executions
    ADD COLUMN idempotency_key VARCHAR(255);

UPDATE job_executions
SET idempotency_key = 'legacy-' || id::text
WHERE idempotency_key IS NULL;

ALTER TABLE job_executions
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE job_executions
    ADD CONSTRAINT uq_job_executions_job_idempotency UNIQUE (job_id, idempotency_key);

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE job_execution_attempts (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    worker_id UUID,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    failure_detail VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_attempts_execution FOREIGN KEY (execution_id) REFERENCES job_executions (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT uq_attempts_execution_number UNIQUE (execution_id, attempt_number),
    CONSTRAINT ck_attempts_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    CONSTRAINT ck_attempts_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_attempts_completion CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

INSERT INTO job_execution_attempts (
    id, execution_id, attempt_number, status, worker_id, started_at, completed_at,
    error_code, error_message, created_at, updated_at, version
)
SELECT
    gen_random_uuid(), id, attempt_number, status, worker_id, started_at, completed_at,
    error_code, error_message, created_at, updated_at, version
FROM job_executions;

CREATE INDEX idx_job_executions_job_created_at ON job_executions (job_id, created_at DESC);
CREATE INDEX idx_job_executions_idempotency ON job_executions (job_id, idempotency_key);
CREATE INDEX idx_attempts_execution_number ON job_execution_attempts (execution_id, attempt_number);
CREATE INDEX idx_attempts_status_created_at ON job_execution_attempts (status, created_at);
