package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationRun;
import br.com.codaline.reconciliation.domain.ReconciliationRun.RunStatus;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationJobListener implements JobExecutionListener {

  private final ReconciliationRunRepository runRepository;

  public ReconciliationJobListener(ReconciliationRunRepository runRepository) {
    this.runRepository = runRepository;
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
      run.setFinishedAt(LocalDateTime.now());
      run.setStatus(
          jobExecution.getStatus() == BatchStatus.COMPLETED
              ? RunStatus.COMPLETED
              : RunStatus.FAILED
      );
      runRepository.save(run);
    });

  }
}
