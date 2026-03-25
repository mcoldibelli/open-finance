package br.com.codaline.reconciliation.batch;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class StagingWriter implements ItemWriter<CipTransaction>, StepExecutionListener {

  private static final String INSERT_SQL =
      "INSERT INTO staging_cip_transactions (run_id, end_to_end_id, debtor_ispb, creditor_ispb, amount) "
          + "VALUES (?, ?, ?, ?, ?)";

  private final JdbcTemplate jdbcTemplate;
  private Long runId;

  public StagingWriter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    runId = stepExecution.getJobExecution()
        .getExecutionContext()
        .getLong("runId");
  }

  @Override
  public void write(@NonNull Chunk<? extends CipTransaction> chunk) {
    var items = chunk.getItems();

    jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
        CipTransaction item = items.get(i);
        ps.setLong(1, runId);
        ps.setString(2, item.endToEndId());
        ps.setString(3, item.debtorIspb());
        ps.setString(4, item.creditorIspb());
        ps.setBigDecimal(5, item.amount());
      }

      @Override
      public int getBatchSize() {
        return items.size();
      }
    });
  }
}
