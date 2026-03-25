package br.com.codaline.reconciliation.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class ReconciliationTasklet implements Tasklet, StepExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationTasklet.class);

  private static final String RECONCILE_SQL = """
      INSERT INTO reconciliation_results
          (run_id, end_to_end_id, debtor_ispb, creditor_ispb, cip_amount, ledger_amount, status, processed_at)
      SELECT
          s.run_id,
          s.end_to_end_id,
          s.debtor_ispb,
          s.creditor_ispb,
          s.amount,
          l.amount,
          CASE
              WHEN l.end_to_end_id IS NULL THEN 'MISSING_IN_LEDGER'
              WHEN s.amount <> l.amount    THEN 'AMOUNT_DIVERGENCE'
              ELSE 'MATCHED'
          END,
          NOW()
      FROM staging_cip_transactions s
      LEFT JOIN ledger_transactions l ON s.end_to_end_id = l.end_to_end_id
      WHERE s.run_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private Long runId;

  public ReconciliationTasklet(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    runId = stepExecution.getJobExecution()
        .getExecutionContext()
        .getLong("runId");
  }

  @Override
  public RepeatStatus execute(@NonNull StepContribution contribution,
      @NonNull ChunkContext chunkContext) {
    int rowsInserted = jdbcTemplate.update(RECONCILE_SQL, runId);
    log.info("Reconciliation completed: {} results inserted for runId={}", rowsInserted, runId);
    return RepeatStatus.FINISHED;
  }
}
