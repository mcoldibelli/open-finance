CREATE TABLE reconciliation_runs
(
    id                      BIGSERIAL PRIMARY KEY,
    file_reference          VARCHAR(255) NOT NULL UNIQUE,
    competence_date         DATE         NOT NULL,
    started_at              TIMESTAMP    NOT NULL,
    finished_at             TIMESTAMP,
    total_records           INTEGER,
    matched_count           INTEGER,
    divergence_count        INTEGER,
    missing_in_ledger_count INTEGER,
    missing_in_cip_count    INTEGER,
    status                  VARCHAR(30)  NOT NULL
);

CREATE TABLE reconciliation_results
(
    id            BIGSERIAL PRIMARY KEY,
    run_id        BIGINT      NOT NULL REFERENCES reconciliation_runs (id),
    end_to_end_id VARCHAR(32) NOT NULL,
    debtor_ispb   CHAR(8)     NOT NULL,
    creditor_ispb CHAR(8)     NOT NULL,
    cip_amount    NUMERIC(15, 2),
    ledger_amount NUMERIC(15, 2),
    status        VARCHAR(30) NOT NULL,
    processed_at  TIMESTAMP   NOT NULL
);


CREATE INDEX idx_result_run ON reconciliation_results (run_id);
CREATE INDEX idx_result_status ON reconciliation_results (status);
CREATE INDEX idx_result_end2end ON reconciliation_results (end_to_end_id);


CREATE TABLE ledger_transactions
(
    id               BIGSERIAL PRIMARY KEY,
    end_to_end_id    VARCHAR(32)    NOT NULL UNIQUE,
    amount           NUMERIC(15, 2) NOT NULL,
    debtor_ispb      CHAR(8)        NOT NULL,
    creditor_ispb    CHAR(8)        NOT NULL,
    transaction_date DATE           NOT NULL
);

CREATE INDEX idx_ledger_end2end ON ledger_transactions (end_to_end_id);