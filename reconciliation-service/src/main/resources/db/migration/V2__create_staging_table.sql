CREATE TABLE staging_cip_transactions
(
    id            BIGSERIAL PRIMARY KEY,
    run_id        BIGINT         NOT NULL REFERENCES reconciliation_runs (id),
    end_to_end_id VARCHAR(32)    NOT NULL,
    debtor_ispb   CHAR(8)        NOT NULL,
    creditor_ispb CHAR(8)        NOT NULL,
    amount        NUMERIC(15, 2) NOT NULL
);

CREATE INDEX idx_staging_run_id ON staging_cip_transactions (run_id);
CREATE INDEX idx_staging_end2end ON staging_cip_transactions (end_to_end_id);
