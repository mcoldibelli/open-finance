package br.com.codaline.reconciliation.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobExecutorConfig {

  @Bean
  public ExecutorService jobExecutor(
      @Value("${reconciliation.executor.core-pool-size:2}") int corePoolSize,
      @Value("${reconciliation.executor.max-pool-size:4}") int maxPoolSize,
      @Value("${reconciliation.executor.queue-capacity:10}") int queueCapacity
  ) {
    return new ThreadPoolExecutor(
        corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(queueCapacity)
    );
  }
}
