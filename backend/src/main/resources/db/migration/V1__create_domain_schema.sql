CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    priority INTEGER NOT NULL,
    timeout_seconds BIGINT NOT NULL,
    max_retries INTEGER NOT NULL,
    initial_backoff_seconds BIGINT NOT NULL,
    max_backoff_seconds BIGINT NOT NULL,
    backoff_multiplier NUMERIC(4, 2) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_jobs_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_jobs_priority CHECK (priority >= 0),
    CONSTRAINT ck_jobs_timeout CHECK (timeout_seconds > 0),
    CONSTRAINT ck_jobs_max_retries CHECK (max_retries >= 0),
    CONSTRAINT ck_jobs_initial_backoff CHECK (initial_backoff_seconds >= 0),
    CONSTRAINT ck_jobs_max_backoff CHECK (max_backoff_seconds >= initial_backoff_seconds),
    CONSTRAINT ck_jobs_backoff_multiplier CHECK (backoff_multiplier >= 1.0)
);

CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_created_by ON jobs (created_by);

CREATE TABLE workers (
    id UUID PRIMARY KEY,
    worker_identifier VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_workers_status CHECK (status IN ('REGISTERED', 'AVAILABLE', 'BUSY', 'DRAINING', 'OFFLINE'))
);

CREATE INDEX idx_workers_status ON workers (status);
CREATE INDEX idx_workers_last_heartbeat ON workers (last_heartbeat);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    sequence_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tasks_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT ck_tasks_sequence_order CHECK (sequence_order >= 0),
    CONSTRAINT ck_tasks_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_tasks_job_order ON tasks (job_id, sequence_order);
CREATE INDEX idx_tasks_status ON tasks (status);

CREATE TABLE task_dependencies (
    task_id UUID NOT NULL,
    depends_on_task_id UUID NOT NULL,
    PRIMARY KEY (task_id, depends_on_task_id),
    CONSTRAINT fk_task_dependencies_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_dependencies_dependency FOREIGN KEY (depends_on_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_dependencies_not_self CHECK (task_id <> depends_on_task_id)
);

CREATE TABLE job_executions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_number INTEGER NOT NULL,
    worker_id UUID,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_job_executions_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_job_executions_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT ck_job_executions_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    CONSTRAINT ck_job_executions_attempt CHECK (attempt_number >= 1),
    CONSTRAINT ck_job_executions_completion CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_job_executions_job_status ON job_executions (job_id, status);
CREATE INDEX idx_job_executions_worker ON job_executions (worker_id);
CREATE INDEX idx_job_executions_started_at ON job_executions (started_at);
