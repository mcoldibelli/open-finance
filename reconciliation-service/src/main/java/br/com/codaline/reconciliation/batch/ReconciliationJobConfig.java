package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
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
  public TaskExecutor partitionTaskExecutor(
      @Value("${reconciliation.partition.thread-pool-size:4}") int poolSize) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(poolSize);
    executor.setMaxPoolSize(poolSize);
    executor.setThreadNamePrefix("reconciliation-partition-");
    executor.initialize();
    return executor;
  }

  @Bean
  public Step masterStep(JobRepository jobRepository, IspbRangePartitioner partitioner,
      Step reconciliationStep, TaskExecutor partitionTaskExecutor) {
    return new StepBuilder("masterStep", jobRepository)
        .partitioner("reconciliationStep", partitioner)
        .step(reconciliationStep)
        .gridSize(4)
        .taskExecutor(partitionTaskExecutor)
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
        .listener(processor)
        .build();
  }
}
