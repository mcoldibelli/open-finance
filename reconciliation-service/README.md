# reconciliation-service

Reconciliacao financeira CIP/CNAB240 para o SPB (Sistema de Pagamentos Brasileiro). Le arquivo
posicional CNAB240, cruza cada transacao com o ledger interno via `endToEndId` e classifica como
`MATCHED`, `AMOUNT_DIVERGENCE`, `MISSING_IN_LEDGER` ou `MISSING_IN_CIP`. Divergencias vao para
Kafka (com DLQ), eventos do job para a trilha de auditoria (BACEN 4.658).

## Arquitetura do job

```
POST /jobs/reconciliation
        |
        v
  ReconciliationJobController (202 Accepted)
        |
        v
  ReconciliationJobListener.beforeJob()
  -> cria ReconciliationRun no banco
        |
        v
  Step 1 — Ingestao
  cipFileReader (FlatFileItemReader) --chunk(500)--> StagingWriter (JdbcTemplate batchUpdate)
  -> le CNAB240, insere na tabela staging_cip_transactions
        |
        v
  Step 2 — Reconciliacao
  ReconciliationTasklet
  -> INSERT INTO reconciliation_results ... SELECT ... LEFT JOIN ledger_transactions
  -> 1 unica query SQL cruza staging com ledger e classifica os resultados
        |
        v
  Step 3 — Publicacao
  JpaPagingItemReader (results divergentes) --chunk(100)--> DivergenceKafkaWriter
  -> publica divergencias no Kafka
  -> falhas vao para DLQ (reconciliation.divergences.dlq)
        |
        v
  ReconciliationJobListener.afterJob()
  -> agrega contadores no ReconciliationRun
  -> limpa staging_cip_transactions do run
  -> publica audit event (JOB_COMPLETED ou JOB_FAILED)

  GET /jobs/reconciliation/{fileReference}
  -> consulta status do run (200 ou 404)
```

## Stack

Spring Batch 5 (chunk processing + tasklet + restart/retry por step), Spring Data JPA +
JdbcTemplate (JPA para entidades, JDBC puro para staging de alto volume), PostgreSQL 16 (Flyway,
`NUMERIC(15,2)` para valores monetarios), Kafka 3.7 (divergencias + DLQ + audit trail com 90 dias
de retencao), Redis (idempotencia de job via `SETNX` por `fileReference`), Testcontainers
(PostgreSQL e Kafka reais nos testes).

## Decisoes de design

### Staging table + SQL JOIN

A abordagem inicial usava `ItemProcessor` com 1 SELECT por transacao. Com 500K transacoes, sao
500K roundtrips. A solucao foi inverter: ingerir tudo numa tabela staging e fazer a reconciliacao
com um unico `INSERT INTO ... SELECT ... LEFT JOIN`. O PostgreSQL resolve o JOIN nativamente com
hash join, indices e paralelismo de query. O arquivo e lido uma unica vez.

```sql
INSERT INTO reconciliation_results (...)
SELECT s.run_id, s.end_to_end_id, s.debtor_ispb, s.creditor_ispb,
       s.amount, l.amount,
       CASE
           WHEN l.end_to_end_id IS NULL THEN 'MISSING_IN_LEDGER'
           WHEN s.amount <> l.amount    THEN 'AMOUNT_DIVERGENCE'
           ELSE 'MATCHED'
       END, NOW()
FROM staging_cip_transactions s
LEFT JOIN ledger_transactions l ON s.end_to_end_id = l.end_to_end_id
WHERE s.run_id = ?
```

Decisao documentada em [ADR-001](../docs/adr/001-staging-table-reconciliation.md).

### 3 steps sequenciais (sem particionamento)

O particionamento por ISPB multiplicava I/O (arquivo lido N vezes) e adicionava complexidade com
`@StepScope` e thread pools. O gargalo real era N+1 queries no banco, nao paralelismo no Java. Com
o JOIN no banco, 1 thread + 1 query e mais rapido que 4 threads + 4 leituras. Cada step falha de
forma isolada — se o Kafka cair, so o step 3 precisa ser re-executado.

### JdbcTemplate no staging, JPA nas entidades

A tabela staging e transiente — inserida no step 1 e deletada no afterJob. Nao precisa de dirty
checking, cache L1 ou cascades. `JdbcTemplate.batchUpdate` envia N INSERTs em um unico roundtrip
JDBC sem overhead de entity manager.

### Idempotencia por fileReference

`Redis.setIfAbsent` (SETNX) por `fileReference` — mesmo pattern do JTI replay no gateway. Evita
race condition de `hasKey` + `set` (duas operacoes separadas). Uma unica operacao atomica.

### DLQ para divergencias

`DeadLetterPublishingRecoverer` com `FixedBackOff(1s, 2 retries)`. Apos 3 tentativas, a mensagem
vai para `reconciliation.divergences.dlq`. Retry infinito travaria o consumer em mensagens
envenenadas; sem DLQ, a mensagem seria descartada. `FixedBackOff` em vez de exponencial porque
controlamos ambos os lados do pipeline.

### BigDecimal: comparacao no SQL

`BigDecimal.equals()` compara valor **e** escala: `100.50.equals(100.5)` retorna `false`. No
PostgreSQL, `NUMERIC` nao tem esse problema. A comparacao fica no SQL (`s.amount <> l.amount`).
O `CipTransactionFieldSetMapper` converte centavos para reais com `movePointLeft(2)` antes do
INSERT.

## Formato CNAB240

Arquivo posicional FEBRABAN: cada linha tem exatamente 240 caracteres, sem delimitadores. A
primeira linha e header (tipo `0`, ignorada com `linesToSkip=1`). Registros de pagamento sao
identificados pelo pattern `???????3A*` — tipo 3 (detalhe) + segmento A (pagamento).

Campos lidos:

| Campo          | Posicoes | Tamanho | Descricao                                    |
|----------------|----------|---------|----------------------------------------------|
| `endToEndId`   | 073–092  | 20      | ID unico da transacao no SPB                 |
| `debtorIspb`   | 093–100  | 8       | ISPB do banco devedor                        |
| `creditorIspb` | 101–108  | 8       | ISPB do banco credor                         |
| `amount`       | 153–168  | 16      | Valor em centavos (dividido por 100)         |

## Schema

Migracoes Flyway (`V1__create_reconciliation_schema.sql`, `V4__create_audit_events.sql`):

```sql
-- Runs de reconciliacao
CREATE TABLE reconciliation_runs (
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

-- Resultados individuais
CREATE TABLE reconciliation_results (
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

-- Ledger de referencia
CREATE TABLE ledger_transactions (
    id               BIGSERIAL PRIMARY KEY,
    end_to_end_id    VARCHAR(32)    NOT NULL UNIQUE,
    amount           NUMERIC(15, 2) NOT NULL,
    debtor_ispb      CHAR(8)        NOT NULL,
    creditor_ispb    CHAR(8)        NOT NULL,
    transaction_date DATE           NOT NULL
);

-- Auditoria append-only (BACEN 4.658)
CREATE TABLE audit_events (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    source         VARCHAR(100) NOT NULL,
    interaction_id VARCHAR(255),
    client_id      VARCHAR(255),
    consent_id     VARCHAR(255),
    details        JSONB,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Trigger impede UPDATE/DELETE
CREATE FUNCTION prevent_audit_modification() RETURNS TRIGGER AS $$
BEGIN RAISE EXCEPTION 'audit_events is append-only'; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
```

## Como rodar

```bash
# infra
docker compose up postgres kafka redis config-server -d

# reconciliation
./mvnw spring-boot:run -pl reconciliation-service

# acionar job
curl -X POST http://localhost:8082/jobs/reconciliation \
  -H "Content-Type: application/json" \
  -d '{"fileReference": "CIP_20240115.txt"}'

# consultar status
curl http://localhost:8082/jobs/reconciliation/CIP_20240115.txt
```

O arquivo CNAB deve estar em `./data/input/` (ou configurado via `RECONCILIATION_INPUT_PATH`).
A resposta do POST e 202 Accepted — o job roda assincrono.

## Testes

```bash
./mvnw test -pl reconciliation-service
```

Testcontainers sobe PostgreSQL e Kafka reais. Testes unitarios cobrem parsing CNAB240, conversao
de valores, consumer Kafka e configuracao de topicos. O teste de integracao (`ReconciliationJobIntegrationTest`) roda o fluxo completo: leitura CNAB -> reconciliacao com ledger -> persistencia -> contadores.
