-- AR Reconciliation Workflow Engine
-- Initial schema: workflow_runs, workflow_events, idempotency_keys

CREATE TABLE workflow_runs (
    id                  UUID            PRIMARY KEY,
    idempotency_key     VARCHAR(128)    NOT NULL UNIQUE,
    current_state       VARCHAR(32)     NOT NULL,
    input_payload       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    stage_outputs       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    retry_counts        JSONB           NOT NULL DEFAULT '{}'::jsonb,
    global_retry_count  INT             NOT NULL DEFAULT 0,
    last_error          TEXT,
    locked_by           VARCHAR(64),
    locked_at           TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_state CHECK (current_state IN (
        'PENDING','INGESTING','INGESTED',
        'MATCHING','MATCHED',
        'VALIDATING','VALIDATED',
        'ROUTING','COMPLETED','FAILED'
    ))
);

-- Index used by reconciliation job to find stuck workflows
CREATE INDEX idx_workflow_runs_state_updated
    ON workflow_runs (current_state, updated_at)
    WHERE current_state NOT IN ('COMPLETED','FAILED');


CREATE TABLE workflow_events (
    id              BIGSERIAL       PRIMARY KEY,
    workflow_id     UUID            NOT NULL REFERENCES workflow_runs(id),
    event_type      VARCHAR(64)     NOT NULL,
    from_state      VARCHAR(32),
    to_state        VARCHAR(32),
    payload         JSONB,
    error_message   TEXT,
    occurred_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workflow_events_workflow_id
    ON workflow_events (workflow_id, occurred_at);


CREATE TABLE idempotency_keys (
    key             VARCHAR(128)    PRIMARY KEY,
    workflow_id     UUID            NOT NULL REFERENCES workflow_runs(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
