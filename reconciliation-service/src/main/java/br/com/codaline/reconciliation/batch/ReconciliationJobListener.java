package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.audit.AuditEvent;
import br.com.codaline.reconciliation.audit.AuditEventPublisher;
import br.com.codaline.reconciliation.domain.ReconciliationResultRepository;
import br.com.codaline.reconciliation.domain.ReconciliationRun;
import br.com.codaline.reconciliation.domain.ReconciliationRun.RunStatus;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReconciliationJobListener implements JobExecutionListener {

  private final ReconciliationRunRepository runRepository;
  private final ReconciliationResultRepository resultRepository;
  private final JdbcTemplate jdbcTemplate;
  private final AuditEventPublisher auditPublisher;

  public ReconciliationJobListener(ReconciliationRunRepository runRepository,
      ReconciliationResultRepository resultRepository, JdbcTemplate jdbcTemplate,
      AuditEventPublisher auditPublisher) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.auditPublisher = auditPublisher;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    String fileReference = jobExecution.getJobParameters().getString("fileReference");
    jobExecution.getExecutionContext().putString("file", fileReference);

    var run = new ReconciliationRun(fileReference, LocalDate.now());
    var saved = runRepository.save(run);

    jobExecution.getExecutionContext().putLong("runId", saved.getId());
    jobExecution.getExecutionContext()
        .putString("competenceDate", run.getCompetenceDate().toString());

    auditPublisher.publish(new AuditEvent(
        "RECONCILIATION_JOB_STARTED",
        LocalDateTime.now(),
        "reconciliation-service",
        null, null, null,
        Map.of("fileReference", fileReference, "runId", saved.getId())
    ));
  }

  @Transactional
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
      run.setMissingInCipCount(
          (int) resultRepository.countByRunAndStatus(run, ReconciliationStatus.MISSING_IN_CIP));

      run.setTotalRecords(
          run.getMatchedCount() + run.getDivergenceCount() + run.getMissingInLedgerCount()
              + run.getMissingInCipCount());
      run.setFinishedAt(LocalDateTime.now());
      run.setStatus(
          jobExecution.getStatus() == BatchStatus.COMPLETED
              ? RunStatus.COMPLETED
              : RunStatus.FAILED
      );
      runRepository.save(run);

      auditPublisher.publish(new AuditEvent(
          run.getStatus() == RunStatus.COMPLETED
              ? "RECONCILIATION_JOB_COMPLETED"
              : "RECONCILIATION_JOB_FAILED",
          LocalDateTime.now(),
          "reconciliation-service",
          null, null, null,
          Map.of(
              "fileReference", fileReference,
              "runId", run.getId(),
              "totalRecords", run.getTotalRecords(),
              "matchedCount", run.getMatchedCount(),
              "divergenceCount", run.getDivergenceCount(),
              "status", run.getStatus().name()
          )
      ));

      jdbcTemplate.update("DELETE FROM staging_cip_transactions WHERE run_id = ?", run.getId());
    });
  }
}
