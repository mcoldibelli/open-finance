package br.com.codaline.reconciliation.batch;

import java.time.LocalDate;
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

  private static final String RECONCILE_CIP_SQL = """
      INSERT INTO reconciliation_results
          (run_id, end_to_end_id, debtor_ispb, creditor_ispb, cip_amount, ledger_amount, status, processed_at)
      SELECT
          s.run_id, s.end_to_end_id, s.debtor_ispb, s.creditor_ispb, s.amount, l.amount,
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

  private static final String RECONCILE_LEDGER_SQL = """
       INSERT INTO reconciliation_results
         (run_id, end_to_end_id, debtor_ispb, creditor_ispb, cip_amount, ledger_amount, status, processed_at)
       SELECT ?, l.end_to_end_id, l.debtor_ispb, l.creditor_ispb, null, l.amount, 'MISSING_IN_CIP', NOW()
       FROM ledger_transactions l
       LEFT JOIN staging_cip_transactions s ON l.end_to_end_id = s.end_to_end_id AND s.run_id = ?
       WHERE l.transaction_date = ? AND s.end_to_end_id IS NULL
      """;

  private final JdbcTemplate jdbcTemplate;
  private Long runId;
  private LocalDate competenceDate;

  public ReconciliationTasklet(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    var context = stepExecution.getJobExecution().getExecutionContext();
    runId = context.getLong("runId");
    competenceDate = LocalDate.parse(context.getString("competenceDate"));
  }

  @Override
  public RepeatStatus execute(@NonNull StepContribution contribution,
      @NonNull ChunkContext chunkContext) {
    int cipRows = jdbcTemplate.update(RECONCILE_CIP_SQL, runId);
    int ledgerRows = jdbcTemplate.update(RECONCILE_LEDGER_SQL, runId, runId, competenceDate);
    log.info(
        "Reconciliation completed: {} CIP results + {} ledger-only results for runId={}",
        cipRows, ledgerRows, runId);
    return RepeatStatus.FINISHED;
  }
}
