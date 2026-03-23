package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
public class ReconciliationJobConfig {

  @Bean
  public Job reconciliationJob(JobRepository jobRepository, ReconciliationJobListener listener,
      Step masterStep) {
    return new JobBuilder("reconciliationJob", jobRepository)
        .listener(listener)
        .start(masterStep)
        .build();
  }

  @Bean
  public Step masterStep(JobRepository jobRepository, IspbRangePartitioner partitioner,
      Step reconciliationStep) {
    return new StepBuilder("masterStep", jobRepository)
        .partitioner("reconciliationStep", partitioner)
        .step(reconciliationStep)
        .gridSize(4)
        .taskExecutor(new SimpleAsyncTaskExecutor())
        .build();
  }

  @Bean
  public Step reconciliationStep(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<CipTransaction> reader,
      ReconciliationProcessor processor,
      ReconciliationResultWriter resultWriter,
      DivergenceKafkaWriter kafkaWriter) {

    CompositeItemWriter<ReconciliationResult> compositeWriter = new CompositeItemWriter<>();
    compositeWriter.setDelegates(List.of(resultWriter, kafkaWriter));

    return new StepBuilder("reconciliationStep", jobRepository)
        .<CipTransaction, ReconciliationResult>chunk(500, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(compositeWriter)
        .build();
  }
}
