-- Trilha de auditoria conforme BACEN Resolução 4.658 (Art. 26)
-- Tabela append-only: trigger impede UPDATE/DELETE
CREATE TABLE audit_events
(
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(50) NOT NULL,
    occurred_at    TIMESTAMP   NOT NULL,
    source         VARCHAR(30) NOT NULL,
    interaction_id VARCHAR(36),
    client_id      VARCHAR(100),
    consent_id     VARCHAR(100),
    details        JSONB,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_event_type ON audit_events (event_type);
CREATE INDEX idx_audit_occurred_at ON audit_events (occurred_at);
CREATE INDEX idx_audit_interaction_id ON audit_events (interaction_id);

CREATE OR REPLACE FUNCTION prevent_audit_modification()
   RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'audit_events is append_only: % not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
