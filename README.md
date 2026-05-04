# AR Reconciliation Workflow Engine

An asynchronous workflow engine for AR (Accounts Receivable) reconciliation built on
Spring Boot, RabbitMQ, and PostgreSQL. Records are processed through four modular
stages — **ingestion → matching → validation → routing** — with full support for
parallel execution, persisted state, retries with exponential backoff, idempotent
submission, and resume-from-last-successful-stage after failures.

## Quick start

Build and run via Docker (recommended):

```bash
docker-compose up --build
```

Or run locally with Maven (requires local Postgres + RabbitMQ):

```bash
./mvnw clean package
./mvnw spring-boot:run
# or:  java -jar target/ar-reconciliation.jar
```

Run tests:

```bash
./mvnw test
```

This brings up Postgres, RabbitMQ (management UI at `http://localhost:15672`,
guest/guest), and two app instances behind a single port.

Submit a workflow:

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-unique-key-1" \
  -d '{"payload":{"invoiceId":"INV-100","paymentId":"INV-100-P","amount":2500.0,"currency":"USD"}}'
```

Check status:

```bash
curl http://localhost:8080/api/workflows/{id}
curl http://localhost:8080/api/workflows/{id}/events
```

Bulk upload Kaggle CSV:

```bash
curl -X POST http://localhost:8080/api/workflows/bulk \
  -F "file=@ar_dataset.csv" \
  -F "idempotencyPrefix=kaggle-run-1"
```

## Architecture

```
Client ──▶ REST API ──▶ WorkflowService ──▶ idempotency check ──▶ workflow_runs (DB)
                                                                       │
                                                                       ▼
                                                       publish(workflowId, INGESTION)
                                                                       │
                                                                       ▼
                                                                ┌─────────────┐
                                                                │  RabbitMQ   │
                                                                │ ar.workflow │
                                                                └─────────────┘
                                                                       │
            ┌──────────────────────┬──────────────────────┬────────────┘
            ▼                      ▼                      ▼
    ar.ingestion.q          ar.matching.q          ar.validation.q ── ar.routing.q
            │                      │                      │                │
            ▼                      ▼                      ▼                ▼
   IngestionConsumer       MatchingConsumer      ValidationConsumer   RoutingConsumer
            │                      │                      │                │
            └──────────────┬───────┴──────────────────────┴────────────────┘
                           ▼
                  StageExecutionService
                  (transactional state transitions
                   + SKIP LOCKED row lock
                   + per-stage retry counter)
                           │
                           ▼
                    workflow_runs / workflow_events  (DB)
```

A `ReconciliationJob` runs every 5 minutes as a safety net: any workflow stuck in a
non-terminal state for >10 minutes is republished to its target queue. Consumer-side
idempotency prevents double-processing.

## How the requirements are satisfied

| Requirement | Implementation |
|---|---|
| Modular stages | Each stage is a `StageHandler` bean; the four are wired into separate `@RabbitListener` consumers with independent queues |
| Random failures + retries | `FailureInjector` simulates configurable per-stage failure rates; `@Retryable` on consumers applies exponential backoff (1s × 2× up to 30s, max 5 attempts per stage) |
| Resume from last successful stage | The state machine persists per-stage progress in `workflow_runs.current_state` + `stage_outputs` (JSONB). When a worker picks up a message it reads state from DB; if the stage already completed, it's a no-op. Crashes and message redeliveries are handled automatically. |
| Parallel execution | RabbitMQ competing consumers + per-stage tunable concurrency (`ar.consumers.*.concurrency`); horizontally scalable (run N app instances) |
| Persistent workflow state | PostgreSQL with JSONB stage outputs, append-only `workflow_events` audit log |
| Idempotent submissions | Unique constraint on `idempotency_key` + lookup-then-insert with race protection via `DataIntegrityViolationException` recovery |

## Key design decisions

**RabbitMQ for signaling, Postgres as source of truth.** Messages contain only
`workflow_id` + `target_stage`. The DB always wins. This means lost messages don't
corrupt state — the reconciliation job catches stuck rows and republishes.

**`SELECT ... FOR UPDATE SKIP LOCKED`** for safe concurrent processing. Two workers
can never operate on the same workflow row at the same time.

**Per-stage retry counters** stored in JSONB. A workflow that flakes once in matching
and once in validation gets two independent counter increments, not one global
strike against it.

**Single Spring Boot artifact, multiple consumers.** All four stage consumers live in
the same JAR — same domain model, same DB connection pool, same deployment. Per-stage
concurrency is tuned independently via `application.yml`. To scale, run more replicas.

**No Redis.** Idempotency uses Postgres unique constraint. Locking uses Postgres
SKIP LOCKED. Keeping one operational dependency is a feature, not a limitation.

## Testing failure scenarios

Failure injection is on by default (`FAILURE_INJECTION=true`). Each stage has a
configurable rate (~15-20% by default). Submit 50 workflows and watch the audit log
to see retry events and DLQ routing.

```bash
# Submit a batch
for i in {1..50}; do
  curl -X POST http://localhost:8080/api/workflows \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: stress-$i" \
    -d "{\"payload\":{\"invoiceId\":\"INV-$i\",\"paymentId\":\"INV-$i-P\",\"amount\":$((100 * i)).0}}"
done

# Watch state distribution
docker exec -it ar-reconciliation_postgres_1 \
  psql -U ar_user -d ar_reconciliation \
  -c "SELECT current_state, COUNT(*) FROM workflow_runs GROUP BY current_state;"
```

Kill an app instance mid-flight; surviving instance(s) will continue processing
existing queue items, and the reconciliation job will pick up anything that was
in-flight on the dead instance.

## Project layout

```
src/main/java/com/ar/reconciliation/
├── ARReconciliationApplication.java
├── api/                    REST controllers + DTOs
├── domain/                 entities + state enums
├── repository/             Spring Data repos (incl. SKIP LOCKED query)
├── service/                WorkflowService, StageExecutionService, EventLogService
├── messaging/              RabbitConfig, WorkflowPublisher, consumers/
├── stages/                 StageHandler impls + FailureInjector
├── reconciliation/         scheduled stuck-workflow recovery
├── config/                 Jackson, Rabbit beans
└── exception/              TransientStageException, PermanentStageException
```

## Demo-time configuration overrides

The following settings were temporarily reduced during local demo/stress testing
to make observable behaviour faster. They have been restored to production defaults
before submission.

| Property | Demo value | Production default | Why changed for demo |
|---|---|---|---|
| `ar.reconciliation.fixed-delay-ms` | `30000` (30 s) | `300000` (5 min) | See stuck-workflow recovery trigger within seconds, not minutes |
| `ar.reconciliation.stuck-threshold-minutes` | `1` | `10` | Mark a workflow as stuck after 1 min so the reconciliation job fires visibly during a short demo run |
| `ar.failure-injection.batch-size` | `10000` | `100` | Submit a larger batch to saturate all consumer threads simultaneously |

No code was changed between the demo and the submitted version — only `application.yml`
values. All settings are overridable via environment variables without recompilation.

## Tech choices justified

- **Spring Boot** — mature ecosystem, first-class support for AMQP, JPA, retries, scheduling
- **RabbitMQ over Kafka** — per-message ack/nack/DLQ semantics fit per-record workflow far better than offset-based streaming
- **PostgreSQL over MongoDB** — `SKIP LOCKED`, ACID transactions, unique constraints — the exact primitives this problem needs. JSONB handles the flexible payload requirement.
- **No Temporal** — we want to *demonstrate* orchestration thinking explicitly, not abstract it behind a framework.
# AR-Reconciliation
