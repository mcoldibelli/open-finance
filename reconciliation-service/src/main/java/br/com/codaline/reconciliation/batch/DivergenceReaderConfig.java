package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DivergenceReaderConfig {

  @Bean
  @StepScope
  public JpaPagingItemReader<ReconciliationResult> divergenceReader(
      EntityManagerFactory entityManagerFactory,
      @Value("#{jobExecutionContext['runId']}") Long runId) {

    return new JpaPagingItemReaderBuilder<ReconciliationResult>()
        .name("divergenceReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString(
            "SELECT r FROM ReconciliationResult r WHERE r.run.id = :runId AND r.status <> 'MATCHED' ORDER BY r.id")
        .parameterValues(Map.of("runId", runId))
        .pageSize(100)
        .build();
  }
}
