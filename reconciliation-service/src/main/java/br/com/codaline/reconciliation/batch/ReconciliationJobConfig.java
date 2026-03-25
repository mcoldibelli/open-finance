package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ReconciliationJobConfig {

  @Bean
  public Job reconciliationJob(JobRepository jobRepository, ReconciliationJobListener listener,
      Step ingestionStep, Step reconciliationStep, Step publicationStep) {
    return new JobBuilder("reconciliationJob", jobRepository)
        .listener(listener)
        .start(ingestionStep)
        .next(reconciliationStep)
        .next(publicationStep)
        .build();
  }

  @Bean
  public Step ingestionStep(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<CipTransaction> cipFileReader,
      StagingWriter stagingWriter) {
    return new StepBuilder("ingestionStep", jobRepository)
        .<CipTransaction, CipTransaction>chunk(500, transactionManager)
        .reader(cipFileReader)
        .writer(stagingWriter)
        .listener(stagingWriter)
        .build();
  }

  @Bean
  public Step reconciliationStep(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ReconciliationTasklet reconciliationTasklet) {
    return new StepBuilder("reconciliationStep", jobRepository)
        .tasklet(reconciliationTasklet, transactionManager)
        .listener(reconciliationTasklet)
        .build();
  }

  @Bean
  public Step publicationStep(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JpaPagingItemReader<ReconciliationResult> divergenceReader,
      DivergenceKafkaWriter kafkaWriter) {
    return new StepBuilder("publicationStep", jobRepository)
        .<ReconciliationResult, ReconciliationResult>chunk(100, transactionManager)
        .reader(divergenceReader)
        .writer(kafkaWriter)
        .build();
  }
}
