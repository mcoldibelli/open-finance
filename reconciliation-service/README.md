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
  IspbRangePartitioner
  -> divide 10.000 ISPBs em N ranges (default: 4)
        |
        +----> Partition 0 (ISPB 0000-2499)
        +----> Partition 1 (ISPB 2500-4999)
        +----> Partition 2 (ISPB 5000-7499)
        +----> Partition 3 (ISPB 7500-9999)
                    |
                    v (cada partição)
              cipFileReader (FlatFileItemReader, @StepScope)
                    |
                    v
              ReconciliationProcessor (@StepScope)
              -> filtra por range ISPB
              -> busca no ledger por endToEndId
              -> classifica: MATCHED | AMOUNT_DIVERGENCE | MISSING_IN_LEDGER
                    |
                    v
              CompositeItemWriter
              |-> ReconciliationResultWriter (saveAll no banco)
              |-> DivergenceKafkaWriter (publica divergências no Kafka)
                    |
                    v
  ReconciliationJobListener.afterJob()
  -> agrega contadores no ReconciliationRun
```

---

## Stack e justificativas

| Tecnologia             | Papel                    | Por que essa escolha                                                                                              |
|------------------------|--------------------------|-------------------------------------------------------------------------------------------------------------------|
| Java 21 (LTS)         | Linguagem                | LTS com suporte estendido, records para DTOs imutaveis, virtual threads disponiveis                               |
| Spring Boot 3.4.5     | Framework                | Auto-configuracao, ecosystem maduro, ProblemDetail nativo (RFC 7807)                                              |
| Spring Batch 5         | Motor de processamento   | Chunk-oriented processing, particionamento nativo, restart/retry, spring-batch-test                               |
| Spring Data JPA        | Persistencia             | Abstracao sobre Hibernate, batch insert com `hibernate.jdbc.batch_size=500`                                       |
| PostgreSQL 16          | Banco de dados           | ACID compliant, `NUMERIC(15,2)` para valores monetarios, partial indexes                                         |
| Apache Kafka 3.7       | Mensageria assincrona    | Pub/sub para divergencias, ordering por `endToEndId` (partition key), retencao configuravel                       |
| Flyway                 | Migracoes                | Versionamento de schema (`V1`, `V2`...), rollback explicito, reprodutivel em CI                                   |
| Testcontainers         | Testes de integracao     | PostgreSQL e Kafka reais em container, sem mocks de infra                                                         |
| Docker Compose         | Orquestracao local       | Ambiente completo com health checks e `depends_on`                                                                |
| Spring Cloud Config    | Configuracao centralizada| Profiles (`local`, `docker`), secrets externalizados via env vars                                                 |

---

## Decisoes de design

### Por que chunk-oriented e nao processar linha por linha

**Problema:** Arquivos CNAB da CIP podem ter centenas de milhares de linhas. Processar uma por uma significa um INSERT por linha, ou seja, N roundtrips ao banco de dados.

**Solucao:** Spring Batch chunk processing com `chunk size = 500` (configurado em `ReconciliationJobConfig`).

**Por que:** O processamento por chunks agrupa as escritas no banco (`saveAll` em vez de `save` em loop), controla os limites transacionais por chunk, e habilita retry/skip no nivel do chunk. Com chunks de 500 + `hibernate.jdbc.batch_size=500`, o Hibernate agrupa todos os INSERTs em um unico JDBC batch, fazendo flush em um unico roundtrip para ate 500 linhas. Linha por linha seria 1 INSERT = 1 roundtrip = performance inviavel para arquivos grandes.

```java
// ReconciliationJobConfig.java — definicao do chunk size
return new StepBuilder("reconciliationStep", jobRepository)
    .<CipTransaction, ReconciliationResult>chunk(500, transactionManager)
    .reader(reader)
    .processor(processor)
    .writer(compositeWriter)
    .build();
```

### Por que particionamento por ISPB e nao por linha do arquivo

**Problema:** Arquivos grandes exigem processamento paralelo. Dividir por linha do arquivo causaria contencao de escrita quando threads diferentes processam transacoes do mesmo ISPB.

**Solucao:** `IspbRangePartitioner` divide os 10.000 ISPBs possiveis em 4 ranges (configuravel via `reconciliation.partition.thread-pool-size`).

**Por que:** ISPB (Identificador do Sistema de Pagamentos Brasileiro) e o codigo atribuido pelo BCB a cada instituicao participante do SPB. Particionar por ISPB garante que cada thread processa um conjunto disjunto de transacoes. Nao ha contencao de lock nas escritas — cada particao grava `endToEndId`s diferentes. Particionamento por linha exigiria leitura com acesso aleatorio ao arquivo ou splitting, e ainda assim arriscaria contencao de escrita em ISPBs sobrepostos.

```java
// IspbRangePartitioner.java
// Partition 0: ISPB 0000-2499
// Partition 1: ISPB 2500-4999
// Partition 2: ISPB 5000-7499
// Partition 3: ISPB 7500-9999
int size = ISPB_TOTAL / gridSize; // 10_000 / 4 = 2500
```

### Por que @StepScope e obrigatorio com particionamento

**Problema:** `ReconciliationProcessor` possui estado mutavel (`currentRun`, `ispbStart`, `ispbEnd`) que varia por particao. Sem escopo adequado, Spring cria um singleton compartilhado entre threads.

**Solucao:** `@StepScope` cria uma nova instancia por execucao de step.

**Por que:** Sem `@StepScope`, o Spring cria um singleton. Com 4 threads escrevendo nos mesmos campos `ispbStart`/`ispbEnd`, ocorre race condition. `@StepScope` garante que cada thread de particao recebe sua propria instancia com seus proprios valores do `ExecutionContext`. O mesmo se aplica ao `cipFileReader` — cada particao precisa de sua propria posicao de cursor no arquivo.

```java
@Component
@StepScope  // sem isso: 4 threads, 1 instância, race condition
public class ReconciliationProcessor implements
    ItemProcessor<CipTransaction, ReconciliationResult>,
    StepExecutionListener {

  private ReconciliationRun currentRun; // estado por particao
  private int ispbStart;                // vem do ExecutionContext
  private int ispbEnd;                  // vem do ExecutionContext
```

### Por que setIfAbsent e nao hasKey + set no Redis

**Problema:** E preciso evitar execucao duplicada do job para o mesmo `fileReference`. Verificar existencia e depois marcar como processado nao e atomico.

**Solucao:** Redis `SETNX` (`setIfAbsent`) — mesmo pattern usado para prevencao de replay de JTI no gateway.

**Por que:** `hasKey` + `set` sao duas operacoes separadas. Entre o check e o set, outra requisicao pode passar (race condition window). `SETNX` e uma unica operacao atomica: se a key existe, retorna `false`; se nao, seta e retorna `true`. Tanto o anti-replay de JTI (gateway) quanto a idempotencia por `fileReference` (reconciliacao) usam esse mesmo pattern porque o problema e identico: check-and-claim atomico.

### Por que saveAll e nao save em loop

**Problema:** Gravar resultados da reconciliacao no banco de dados. Um loop de `save()` executa N INSERT statements individuais com N roundtrips.

**Solucao:** `ReconciliationResultWriter` usa `repository.saveAll(chunk.getItems())`.

**Por que:** Com `hibernate.jdbc.batch_size=500` e `order_inserts=true` (ambos configurados no `application.yml`), o Hibernate agrupa os INSERTs em um unico JDBC batch. `saveAll()` com batch config: 1 roundtrip para ate 500 linhas. Isso e critico para performance ao processar arquivos com 100k+ transacoes.

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 500
        order_inserts: true
        order_updates: true
```

### Por que o JobListener cria o ReconciliationRun no beforeJob e nao no beforeStep

**Problema:** A execucao precisa ser rastreada como um unico `ReconciliationRun`. Criar no `beforeStep` criaria multiplos runs para o mesmo arquivo.

**Solucao:** `ReconciliationJobListener.beforeJob` cria a entidade `ReconciliationRun` e armazena o ID no `JobExecution.executionContext`.

**Por que:** Um job particionado tem 1 Job com N Steps (um por particao). Criar no `beforeStep` geraria N runs para o mesmo arquivo. `beforeJob` executa uma unica vez, cria um unico run, e todas as particoes acessam o mesmo `runId` via `JobExecution.executionContext` compartilhado. O `afterJob` entao agrega os contadores de todas as particoes nesse unico run.

```java
// ReconciliationJobListener.java
@Override
public void beforeJob(JobExecution jobExecution) {
    var run = new ReconciliationRun(fileReference, LocalDate.now());
    var saved = runRepository.save(run);
    jobExecution.getExecutionContext().putLong("runId", saved.getId());
    // todas as particoes leem "runId" do executionContext do Job
}
```

### Por que compareTo e nao equals para BigDecimal

**Problema:** Comparar valores monetarios do arquivo CIP (CNAB) com valores do ledger. `BigDecimal.equals()` compara valor E escala, causando falsos negativos.

**Solucao:** `item.amount().compareTo(ledgerAmount) == 0`.

**Por que:** `BigDecimal.equals()` compara valor **e** escala: `new BigDecimal("100.50").equals(new BigDecimal("100.5"))` retorna `false`. `compareTo()` compara apenas o valor numerico: retorna `0` para valores iguais independente da escala. Valores do CNAB vem em centavos (`movePointLeft(2)` resulta em escala 2) enquanto valores do ledger podem ter escala diferente. Para comparacoes monetarias, `compareTo == 0` e a abordagem correta.

```java
// ReconciliationProcessor.java
var status = item.amount().compareTo(ledgerAmount) == 0
    ? ReconciliationStatus.MATCHED
    : ReconciliationStatus.AMOUNT_DIVERGENCE;
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
- [ ] Dead letter queue para divergencias que falharam no Kafka
- [ ] Dashboard de monitoramento de runs (metricas do Spring Batch Actuator)
- [ ] Alertas quando `divergenceRate > threshold` configuravel
- [ ] Endpoint `GET /jobs/reconciliation/{fileReference}` para consultar status do run
- [ ] `MISSING_IN_CIP`: transacoes no ledger que nao existem no arquivo CIP (requer segundo step)
