package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResultRepository;
import br.com.codaline.reconciliation.domain.ReconciliationRun;
import br.com.codaline.reconciliation.domain.ReconciliationRun.RunStatus;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationJobListener implements JobExecutionListener {

  private final ReconciliationRunRepository runRepository;
  private final ReconciliationResultRepository resultRepository;
  private final JdbcTemplate jdbcTemplate;

  public ReconciliationJobListener(ReconciliationRunRepository runRepository,
      ReconciliationResultRepository resultRepository, JdbcTemplate jdbcTemplate) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    String fileReference = jobExecution.getJobParameters().getString("fileReference");
    jobExecution.getExecutionContext().putString("file", fileReference);

    var run = new ReconciliationRun(fileReference, LocalDate.now());
    var saved = runRepository.save(run);

    jobExecution.getExecutionContext().putLong("runId", saved.getId());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    String fileReference = jobExecution.getJobParameters().getString("fileReference");

    runRepository.findByFileReference(fileReference).ifPresent(run -> {
      run.setMatchedCount(
          (int) resultRepository.countByRunAndStatus(run, ReconciliationStatus.MATCHED));
      run.setDivergenceCount(
          (int) resultRepository.countByRunAndStatus(run, ReconciliationStatus.AMOUNT_DIVERGENCE));
      run.setMissingInLedgerCount(
          (int) resultRepository.countByRunAndStatus(run, ReconciliationStatus.MISSING_IN_LEDGER));
      run.setTotalRecords(
          run.getMatchedCount() + run.getDivergenceCount() + run.getMissingInLedgerCount());
      run.setFinishedAt(LocalDateTime.now());
      run.setStatus(
          jobExecution.getStatus() == BatchStatus.COMPLETED
              ? RunStatus.COMPLETED
              : RunStatus.FAILED
      );
      runRepository.save(run);
      jdbcTemplate.update("DELETE FROM staging_cip_transactions WHERE run_id = ?", run.getId());
    });
  }
}
