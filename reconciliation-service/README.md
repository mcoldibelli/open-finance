# reconciliation-service

Servico de reconciliacao financeira que compara transacoes registradas no ledger interno com os arquivos CNAB240 enviados pela CIP (Camara Interbancaria de Pagamentos) no ambito do SPB (Sistema de Pagamentos Brasileiro), regulado pelo BACEN.

O modulo le um arquivo posicional CNAB240, cruza cada transacao com o ledger via `endToEndId`, e classifica o resultado como `MATCHED`, `AMOUNT_DIVERGENCE` ou `MISSING_IN_LEDGER`. Divergencias sao publicadas em um topico Kafka para consumo por outros servicos.

---

## Arquitetura do Job

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
  -> publica divergencias (AMOUNT_DIVERGENCE, MISSING_IN_LEDGER) no Kafka
        |
        v
  ReconciliationJobListener.afterJob()
  -> agrega contadores no ReconciliationRun
  -> limpa staging_cip_transactions do run
```

Decisao arquitetural documentada em [ADR-001](../docs/adr/001-staging-table-reconciliation.md).

---

## Stack e justificativas

| Tecnologia             | Papel                    | Por que essa escolha                                                                                              |
|------------------------|--------------------------|-------------------------------------------------------------------------------------------------------------------|
| Java 21 (LTS)         | Linguagem                | LTS com suporte estendido, records para DTOs imutaveis, virtual threads disponiveis                               |
| Spring Boot 3.4.5     | Framework                | Auto-configuracao, ecosystem maduro, ProblemDetail nativo (RFC 7807)                                              |
| Spring Batch 5         | Motor de processamento   | Chunk-oriented processing para ingestao, tasklet para reconciliacao SQL, restart/retry por step                   |
| Spring Data JPA        | Persistencia             | Abstracao sobre Hibernate, batch insert com `hibernate.jdbc.batch_size=500`                                       |
| PostgreSQL 16          | Banco de dados           | ACID compliant, `NUMERIC(15,2)` para valores monetarios, partial indexes                                         |
| Apache Kafka 3.7       | Mensageria assincrona    | Pub/sub para divergencias, ordering por `endToEndId` (partition key), retencao configuravel                       |
| Flyway                 | Migracoes                | Versionamento de schema (`V1`, `V2`...), rollback explicito, reprodutivel em CI                                   |
| Testcontainers         | Testes de integracao     | PostgreSQL e Kafka reais em container, sem mocks de infra                                                         |
| Docker Compose         | Orquestracao local       | Ambiente completo com health checks e `depends_on`                                                                |
| Spring Cloud Config    | Configuracao centralizada| Profiles (`local`, `docker`), secrets externalizados via env vars                                                 |

---

## Decisoes de design

### Por que staging table + SQL JOIN e nao lookup por item

**Problema:** A abordagem anterior usava `ItemProcessor` com 1 SELECT por transacao (`findByEndToEndId`). Com 500K transacoes, sao 500K roundtrips ao banco. Alem disso, o particionamento por ISPB lia o arquivo N vezes.

**Solucao:** Ingestao em tabela staging + reconciliacao via `INSERT INTO ... SELECT ... LEFT JOIN`.

**Por que:** A reconciliacao e fundamentalmente um JOIN entre dois datasets. O PostgreSQL resolve isso nativamente com hash join ou merge join, utilizando indices e paralelismo de query. Um unico `LEFT JOIN` indexado substitui N SELECTs individuais. O arquivo e lido uma unica vez (step de ingestao), e a reconciliacao e uma unica query atomica. Decisao documentada em [ADR-001](../docs/adr/001-staging-table-reconciliation.md).

```sql
-- ReconciliationTasklet.java — reconciliacao em uma unica query
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

### Por que 3 steps sequenciais e nao particionamento

**Problema:** O particionamento por ISPB multiplicava I/O (arquivo lido 4x) e adicionava complexidade com `@StepScope`, thread pools e `ExecutionContext` por particao.

**Solucao:** 3 steps sequenciais — ingestao (chunk), reconciliacao (tasklet), publicacao (chunk).

**Por que:** O gargalo real era o N+1 queries no banco, nao a falta de paralelismo no Java. O PostgreSQL paraleliza queries internamente quando necessario. Com o JOIN no banco, o processamento e mais rapido com 1 thread + 1 query do que com 4 threads + 4 leituras de arquivo. Cada step tem responsabilidade unica e falha de forma isolada — se o Kafka cair, apenas o step 3 e re-executado.

### Por que setIfAbsent e nao hasKey + set no Redis

**Problema:** E preciso evitar execucao duplicada do job para o mesmo `fileReference`. Verificar existencia e depois marcar como processado nao e atomico.

**Solucao:** Redis `SETNX` (`setIfAbsent`) — mesmo pattern usado para prevencao de replay de JTI no gateway.

**Por que:** `hasKey` + `set` sao duas operacoes separadas. Entre o check e o set, outra requisicao pode passar (race condition window). `SETNX` e uma unica operacao atomica: se a key existe, retorna `false`; se nao, seta e retorna `true`. Tanto o anti-replay de JTI (gateway) quanto a idempotencia por `fileReference` (reconciliacao) usam esse mesmo pattern porque o problema e identico: check-and-claim atomico.

### Por que JdbcTemplate no staging e nao JPA

**Problema:** Inserir centenas de milhares de linhas do CNAB na tabela staging.

**Solucao:** `StagingWriter` usa `JdbcTemplate.batchUpdate` com `BatchPreparedStatementSetter`.

**Por que:** A tabela staging e transiente — nao precisa de ciclo de vida JPA (dirty checking, cache L1, cascades). `JdbcTemplate.batchUpdate` envia N INSERTs em um unico roundtrip JDBC, sem overhead de entity manager. Para dados que serao descartados apos o job, JDBC puro e a escolha correta.

### Por que o JobListener cria o run no beforeJob e limpa staging no afterJob

**Problema:** O `runId` precisa existir antes do step de ingestao (para o FK na staging), e a staging precisa ser limpa apos a conclusao.

**Solucao:** `beforeJob` cria o `ReconciliationRun` e armazena `runId` no `ExecutionContext`. `afterJob` agrega contadores e executa `DELETE FROM staging_cip_transactions WHERE run_id = ?`.

**Por que:** O `beforeJob` executa uma unica vez antes de todos os steps, garantindo que o `runId` esta disponivel para ingestao, reconciliacao e publicacao. O cleanup no `afterJob` garante que dados staging nao acumulam — mesmo em caso de falha, o proximo run nao e afetado por dados residuais.

### Por que compareTo e nao equals para BigDecimal

**Problema:** Comparar valores monetarios do arquivo CIP (CNAB) com valores do ledger. `BigDecimal.equals()` compara valor E escala, causando falsos negativos.

**Solucao:** Na query SQL, `s.amount <> l.amount` (operador `<>` do PostgreSQL para `NUMERIC`).

**Por que:** `BigDecimal.equals()` em Java compara valor **e** escala: `new BigDecimal("100.50").equals(new BigDecimal("100.5"))` retorna `false`. No PostgreSQL, `NUMERIC` nao tem esse problema — `100.50 <> 100.5` avalia corretamente como `false` (valores iguais). Ao mover a comparacao para SQL, eliminamos esse risco. O `CipTransactionFieldSetMapper` continua usando `movePointLeft(2)` para converter centavos em reais antes do INSERT na staging.

```sql
-- ReconciliationTasklet.java
CASE
    WHEN l.end_to_end_id IS NULL THEN 'MISSING_IN_LEDGER'
    WHEN s.amount <> l.amount    THEN 'AMOUNT_DIVERGENCE'
    ELSE 'MATCHED'
END
```

---

## Formato CNAB240

CNAB240 e um formato de arquivo posicional (flat-file) definido pela FEBRABAN (Federacao Brasileira de Bancos). E utilizado pela CIP (Camara Interbancaria de Pagamentos) no SPB (Sistema de Pagamentos Brasileiro), regulado pelo BACEN.

Caracteristicas do formato:
- Cada linha tem exatamente **240 caracteres**, sem delimitadores (nao e CSV, JSON, ou XML)
- Campos sao extraidos por **posicoes fixas** (1-indexed conforme a especificacao FEBRABAN)
- A primeira linha e o **header de arquivo** (tipo de registro `0`) e e ignorada (`linesToSkip=1`)
- Registros de pagamento sao identificados por **pattern matching**: registro tipo 3, segmento A

### Pattern matching: `???????3A*`

O pattern `???????3A*` e utilizado pelo `PatternMatchingCompositeLineMapper` do Spring Batch:

- `?` — corresponde a qualquer caractere (posicoes 1-7 sao ignoradas)
- `3` — posicao 8 deve ser `'3'` (registro tipo 3 = detalhe)
- `A` — posicao 9 deve ser `'A'` (segmento A = dados do pagamento)
- `*` — corresponde ao restante da linha

Linhas que nao casam com o pattern (header, trailer, outros segmentos) sao mapeadas para `null` e ignoradas pelo Spring Batch.

### Campos lidos

| Campo          | Posicoes CNAB | Tamanho | Tipo          | Descricao                                           |
|----------------|---------------|---------|---------------|-----------------------------------------------------|
| `endToEndId`   | 073–092       | 20      | Alfanumerico  | Identificador unico da transacao no SPB             |
| `debtorIspb`   | 093–100       | 8       | Numerico      | ISPB do banco devedor (codigo BCB)                  |
| `creditorIspb` | 101–108       | 8       | Numerico      | ISPB do banco credor                                |
| `amount`       | 153–168       | 16      | Numerico      | Valor em centavos (dividido por 100 na leitura)     |

A conversao de centavos para reais e feita no `CipTransactionFieldSetMapper`:

```java
new BigDecimal(rawAmount).movePointLeft(2)
// "0000000000010050" -> BigDecimal("100.50")
```

---

## Schema do banco

Migracoes gerenciadas pelo Flyway (`V1__create_reconciliation_schema.sql`):

```sql
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

CREATE TABLE ledger_transactions (
    id               BIGSERIAL PRIMARY KEY,
    end_to_end_id    VARCHAR(32)    NOT NULL UNIQUE,
    amount           NUMERIC(15, 2) NOT NULL,
    debtor_ispb      CHAR(8)        NOT NULL,
    creditor_ispb    CHAR(8)        NOT NULL,
    transaction_date DATE           NOT NULL
);
```

Valores monetarios usam `NUMERIC(15,2)` — tipo exato, sem ponto flutuante. Indices em `end_to_end_id`, `run_id` e `status` para consultas frequentes.

---

## Como rodar localmente

### Pre-requisitos

- Java 21
- Docker e Docker Compose
- Maven (ou use o wrapper `./mvnw`)

### Subir infraestrutura

```bash
# subir infraestrutura
docker compose up postgres kafka redis -d
```

### Subir config-server

```bash
./mvnw spring-boot:run -pl config-server
```

### Acionar o job

```bash
curl -X POST http://localhost:8082/jobs/reconciliation \
  -H "Content-Type: application/json" \
  -d '{"fileReference": "CIP_20240115.txt"}'
```

### Configuracao necessaria

- O arquivo `.env` deve conter: `REDIS_PASSWORD`, `POSTGRES_PASSWORD`
- O arquivo CNAB deve estar em `./data/input` (ou configurado via variavel de ambiente `RECONCILIATION_INPUT_PATH`)
- A resposta do endpoint e **202 Accepted** — o job roda de forma assincrona via `CompletableFuture.runAsync`
- Para consultar o status do job, verificar a tabela `reconciliation_runs` no banco:

```sql
SELECT file_reference, status, total_records, matched_count,
       divergence_count, missing_in_ledger_count, started_at, finished_at
FROM reconciliation_runs
WHERE file_reference = 'CIP_20240115.txt';
```

---

## Testes

| Classe                               | Tipo        | O que valida                                                                                                                                                      |
|--------------------------------------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CipFileReaderTest`                  | Unitario    | Parsing de arquivo CNAB240: leitura de campos posicionais, conversao de centavos para `BigDecimal`, header ignorado, arquivo sem transacoes                       |
| `CipTransactionFieldSetMapperTest`   | Unitario    | Validacao do campo `amount`: valor numerico valido, rejeicao de caracteres invalidos, rejeicao de valor vazio, conversao `movePointLeft(2)`                       |
| `ReconciliationJobIntegrationTest`   | Integracao  | Fluxo completo do job: leitura CNAB -> reconciliacao com ledger -> persistencia de resultados -> contadores agregados no run. Usa Testcontainers (PostgreSQL + Kafka reais) |

Os testes de integracao estendem `IntegrationTestBase`, que configura containers reais via Testcontainers:
- PostgreSQL 16 (alpine) para o banco de dados
- Kafka (Confluent CP 7.6.0) para mensageria

### Como executar

```bash
# todos os testes
./mvnw test -pl reconciliation-service

# apenas integracao (requer Docker rodando)
./mvnw test -pl reconciliation-service -Dtest="ReconciliationJobIntegrationTest"
```

---

## O que falta (roadmap)

- [ ] Integracao real com S3 para leitura do arquivo (substituir `FileSystemResource` por `S3Resource`)
- [x] Dead letter queue para divergencias que falharam no Kafka
- [ ] Dashboard de monitoramento de runs (metricas do Spring Batch Actuator)
- [ ] Alertas quando `divergenceRate > threshold` configuravel
- [x] Endpoint `GET /jobs/reconciliation/{fileReference}` para consultar status do run
- [x] `MISSING_IN_CIP`: transacoes no ledger que nao existem no arquivo CIP (dois LEFT JOINs no `ReconciliationTasklet`)
