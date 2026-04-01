package br.com.codaline.reconciliation.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic auditEventsTopic(@Value("${kafka.topics.audit}") String auditTopic) {
    return TopicBuilder.name(auditTopic)
        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(90).toMillis()))
        .build();
  }

  @Bean
  public NewTopic divergencesDlqTopic(
      @Value("${kafka.topics.divergences}") String divergencesTopic) {
    return TopicBuilder.name(divergencesTopic + ".dlq").build();
  }
}
