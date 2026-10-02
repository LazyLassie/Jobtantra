CREATE TABLE job_schedules (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE,
    schedule_type VARCHAR(16) NOT NULL,
    cron_expression VARCHAR(120),
    one_time_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_scheduled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_job_schedules_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_schedules_type CHECK (schedule_type IN ('ONE_TIME', 'CRON')),
    CONSTRAINT ck_job_schedules_definition CHECK (
        (schedule_type = 'ONE_TIME' AND one_time_at IS NOT NULL AND cron_expression IS NULL)
        OR (schedule_type = 'CRON' AND cron_expression IS NOT NULL AND one_time_at IS NULL)
    )
);

CREATE INDEX idx_job_schedules_due ON job_schedules (next_run_at);
