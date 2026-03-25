package br.com.codaline.reconciliation.api;

import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class ReconciliationJobController {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationJobController.class);

  private final JobLauncher jobLauncher;
  private final Job reconciliationJob;
  private final ReconciliationRunRepository runRepository;

  public ReconciliationJobController(JobLauncher jobLauncher,
      @Qualifier("reconciliationJob") Job reconciliationJob,
      ReconciliationRunRepository runRepository) {
    this.jobLauncher = jobLauncher;
    this.reconciliationJob = reconciliationJob;
    this.runRepository = runRepository;
  }

  @PostMapping("/reconciliation")
  public ResponseEntity<JobLaunchResponse> launchReconciliation(
      @Valid @RequestBody JobLaunchRequest request) {

    if (runRepository.findByFileReference(request.fileReference()).isPresent()) {
      throw new DuplicateJobException(request.fileReference());
    }

    JobParameters params = new JobParametersBuilder()
        .addString("fileReference", request.fileReference())
        .toJobParameters();

    CompletableFuture.runAsync(() -> {
      try {
        jobLauncher.run(reconciliationJob, params);
      } catch (Exception ex) {
        log.error("Job execution failed for file: {}", request.fileReference(), ex);
      }
    });

    log.info("Reconciliation job accepted for file: {}", request.fileReference());
    return ResponseEntity.accepted().body(JobLaunchResponse.accepted(request.fileReference()));
  }
}
