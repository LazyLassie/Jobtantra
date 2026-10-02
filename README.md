# JobTantra

JobTantra is a distributed job orchestration platform. Phase 1 establishes the domain model and PostgreSQL persistence foundation; execution, queues, scheduling, authentication, and frontend work are intentionally deferred.

## Architecture

The backend is a Spring Boot 3.5.12 Maven application targeting Java 22.

- `com.jobtantra.domain.model`: lifecycle-aware domain entities and enums.
- `com.jobtantra.infrastructure.persistence.repository`: Spring Data repository ports and adapters for persistence.
- `com.jobtantra.common`: shared API responses, validation errors, correlation IDs, and web configuration.
- `com.jobtantra.security`: current stateless security filter-chain foundation.
- `src/main/resources/db/migration`: Flyway-managed PostgreSQL schema.

The persistence model uses UUID identifiers, PostgreSQL `jsonb` for extensible configuration and metadata, optimistic locking, explicit foreign keys, and application-managed audit timestamps.

## Domain Model

- **Job**: the top-level definition, including lifecycle status, priority, timeout, retry policy, creator, and configuration.
- **Task**: an ordered unit belonging to a job. Tasks can reference prerequisite tasks through `task_dependencies`.
- **JobExecution**: one attempt to run a job, associated with a job and optionally a worker.
- **Worker**: a registered execution-capable worker with availability status and heartbeat metadata.
- **RetryPolicy**: an embeddable job value object containing retry limits and exponential-backoff settings.

Job and execution lifecycle changes are represented by enums and guarded transition methods. The execution engine is not part of this phase.

## PostgreSQL Setup

Create a PostgreSQL database named `jobtantra`, then configure the application with environment variables:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/jobtantra"
$env:DATABASE_USERNAME = "jobtantra"
$env:DATABASE_PASSWORD = "your-local-password"
```

The local profile is active by default. Hibernate uses `ddl-auto: validate`, and Flyway applies migrations on startup. The application never creates or updates the schema through Hibernate.

## Run

From `backend`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
mvn spring-boot:run
```

The service listens on port `8080` by default. The actuator health endpoint is available at `/actuator/health`.

## Job API

Job routes are currently unauthenticated while authentication remains deferred to a later phase.

Create a job:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/jobs `
	-H "Content-Type: application/json" `
	-d '{"name":"daily-import","description":"Import source data","createdBy":"scheduler","priority":20,"timeoutSeconds":600,"retryPolicy":{"maxRetries":3,"initialBackoffSeconds":30,"maxBackoffSeconds":3600,"backoffMultiplier":2.0},"configuration":{"source":"s3"}}'
```

List jobs with pagination and status filtering:

```powershell
curl.exe "http://localhost:8080/api/v1/jobs?status=ACTIVE&page=0&size=20"
```

Get a job:

```powershell
curl.exe http://localhost:8080/api/v1/jobs/{job-id}
```

Activate a newly created DRAFT job:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> -X POST http://localhost:8080/api/v1/jobs/{job-id}/activate
```

Cancel an ACTIVE or PAUSED job:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> -X POST http://localhost:8080/api/v1/jobs/{job-id}/cancel
```

## Execution API

Phase 3 creates logical executions and queued attempts only. It does not dispatch tasks or introduce workers, queues, Redis, Kafka, or scheduling.

Create one idempotent execution for an ACTIVE job:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> `
	-X POST http://localhost:8080/api/v1/jobs/{job-id}/executions `
	-H "Idempotency-Key: import-2026-09-19-001"
```

Submitting the same `Idempotency-Key` for the same job returns the existing logical execution instead of creating a duplicate. Retries create another attempt under that execution, not another execution.

Retrieve, cancel, or retry an execution:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> http://localhost:8080/api/v1/executions/{execution-id}
curl.exe -u user:<GENERATED_PASSWORD> -X POST http://localhost:8080/api/v1/executions/{execution-id}/cancel
curl.exe -u user:<GENERATED_PASSWORD> -X POST http://localhost:8080/api/v1/executions/{execution-id}/retry
```

Execution cancellation is independent from job cancellation. Cancelling a job does not automatically cancel its executions.

## Scheduling API

Schedules are stored in UTC and are evaluated only for `ACTIVE` jobs. Scheduling creates queued executions through the existing execution service; it does not add workers, queues, Redis, Kafka, or task dispatching.

Create or replace a one-time schedule:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> `
	-X PUT http://localhost:8080/api/v1/jobs/{job-id}/schedule `
	-H "Content-Type: application/json" `
	-d '{"type":"ONE_TIME","oneTimeAt":"2026-10-01T12:00:00Z"}'
```

Create or replace a cron schedule:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> `
	-X PUT http://localhost:8080/api/v1/jobs/{job-id}/schedule `
	-H "Content-Type: application/json" `
	-d '{"type":"CRON","cronExpression":"0 */15 * * * *"}'
```

Inspect or remove a schedule:

```powershell
curl.exe -u user:<GENERATED_PASSWORD> http://localhost:8080/api/v1/jobs/{job-id}/schedule
curl.exe -u user:<GENERATED_PASSWORD> -X DELETE http://localhost:8080/api/v1/jobs/{job-id}/schedule
```

The create response is wrapped in the standard API envelope:

```json
{
	"data": {
		"id": "2f6f6c51-0f12-4f1b-8d25-0c3dc95f6c1a",
		"name": "daily-import",
		"status": "DRAFT",
		"priority": 20,
		"timeoutSeconds": 600
	},
	"timestamp": "2026-09-17T10:00:00Z"
}
```

## Migrations

Flyway migrations are under `backend/src/main/resources/db/migration`. The initial migration creates jobs, workers, tasks, task dependencies, and job executions with indexes, checks, and foreign keys.

For normal application startup, `spring.flyway.enabled=true` runs the migrations automatically. Start the service with `mvn spring-boot:run` after setting the database environment variables; Flyway applies pending migrations before the application is ready.

## Tests

Run the unit and persistence tests with:

```powershell
mvn clean test
```

Unit tests cover entity validation and lifecycle transitions. Repository tests use Testcontainers with PostgreSQL and are skipped when a Docker daemon is unavailable; they never assume a locally running database.