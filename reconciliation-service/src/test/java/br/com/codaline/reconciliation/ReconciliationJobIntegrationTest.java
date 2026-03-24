package br.com.codaline.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.codaline.reconciliation.batch.CnabFileBuilder;
import br.com.codaline.reconciliation.domain.LedgerTransaction;
import br.com.codaline.reconciliation.domain.LedgerTransactionRepository;
import br.com.codaline.reconciliation.domain.ReconciliationResultRepository;
import br.com.codaline.reconciliation.domain.ReconciliationRun.RunStatus;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ReconciliationJobIntegrationTest extends IntegrationTestBase {

  private static final Path CNAB_FILE;

  static {
    try {
      CNAB_FILE = Files.createTempFile("cnab-test-", ".txt");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void jobProperties(DynamicPropertyRegistry registry) {
    registry.add("reconciliation.files.input-path", () -> CNAB_FILE.toAbsolutePath().toString());
  }

  @AfterAll
  static void cleanup() throws Exception {
    Files.deleteIfExists(CNAB_FILE);
  }

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  @Qualifier("reconciliationJob")
  private Job job;

  @Autowired
  private ReconciliationRunRepository runRepository;

  @Autowired
  private ReconciliationResultRepository resultRepository;

  @Autowired
  private LedgerTransactionRepository ledgerRepository;

  @BeforeEach
  void setUp() {
    resultRepository.deleteAll();
    runRepository.deleteAll();
    ledgerRepository.deleteAll();
  }

  @Test
  void dado_arquivoComTransacoesVariadas_quando_executarJob_entao_reconciliaCorretamente()
      throws Exception {
    // Arrange
    String fileReference = "CNAB_2026-03-24_VARIADAS";
    String matchedId = "E0000000100000000001";
    String divergentId = "E0000000200000000002";
    String missingId = "E0000000300000000003";

    String cnabContent = CnabFileBuilder.buildHeaderLine() + "\n"
        + CnabFileBuilder.buildSegmentALine(matchedId, "00000100", "00000200", "0000000000010050") + "\n"
        + CnabFileBuilder.buildSegmentALine(divergentId, "00000200", "00000300", "0000000000020000") + "\n"
        + CnabFileBuilder.buildSegmentALine(missingId, "00000300", "00000400", "0000000000005000") + "\n";
    Files.writeString(CNAB_FILE, cnabContent);

    var ledgerMatched = new LedgerTransaction();
    ledgerMatched.setEndToEndId(matchedId);
    ledgerMatched.setAmount(new BigDecimal("100.50"));
    ledgerMatched.setDebtorIspb("00000100");
    ledgerMatched.setCreditorIspb("00000200");
    ledgerMatched.setTransactinDate(LocalDate.now());
    ledgerRepository.save(ledgerMatched);

    var ledgerDivergent = new LedgerTransaction();
    ledgerDivergent.setEndToEndId(divergentId);
    ledgerDivergent.setAmount(new BigDecimal("199.99"));
    ledgerDivergent.setDebtorIspb("00000200");
    ledgerDivergent.setCreditorIspb("00000300");
    ledgerDivergent.setTransactinDate(LocalDate.now());
    ledgerRepository.save(ledgerDivergent);

    JobParameters params = new JobParametersBuilder()
        .addString("fileReference", fileReference)
        .toJobParameters();

    // Act
    var execution = jobLauncher.run(job, params);

    // Assert
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    var run = runRepository.findByFileReference(fileReference);
    assertThat(run).isPresent();
    assertThat(run.get().getStatus()).isEqualTo(RunStatus.COMPLETED);
    assertThat(run.get().getMatchedCount()).isEqualTo(1);
    assertThat(run.get().getDivergenceCount()).isEqualTo(1);
    assertThat(run.get().getMissingInLedgerCount()).isEqualTo(1);
    assertThat(run.get().getTotalRecords()).isEqualTo(3);

    assertThat(resultRepository.count()).isEqualTo(3);
  }

  @Test
  void dado_arquivoSemTransacoes_quando_executarJob_entao_completaSemResultados() throws Exception {
    // Arrange
    String fileReference = "CNAB_2026-03-24_VAZIO";
    String cnabContent = CnabFileBuilder.buildHeaderLine() + "\n"
        + CnabFileBuilder.buildHeaderLine() + "\n";
    Files.writeString(CNAB_FILE, cnabContent);

    JobParameters params = new JobParametersBuilder()
        .addString("fileReference", fileReference)
        .toJobParameters();

    // Act
    var execution = jobLauncher.run(job, params);

    // Assert
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    var run = runRepository.findByFileReference(fileReference);
    assertThat(run).isPresent();
    assertThat(run.get().getStatus()).isEqualTo(RunStatus.COMPLETED);
    assertThat(run.get().getTotalRecords()).isEqualTo(0);

    assertThat(resultRepository.count()).isZero();
  }
}
