package br.com.codaline.reconciliation.config;


import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.time.Duration;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigTest {

  private final KafkaTopicConfig config = new KafkaTopicConfig();

  @Test
  void given_auditTopic_when_created_then_retentionIs90Days() {
    // Arrange and Act
    var topic = config.auditEventsTopic("audit.events");

    // Assert
    assertThat(topic.name()).isEqualTo("audit.events");
    assertThat(topic.configs()).containsEntry(TopicConfig.RETENTION_MS_CONFIG,
        String.valueOf(Duration.ofDays(90).toMillis()));
  }

  @Test
  void given_divergencesTopic_when_created_then_dlqTopicNameIsCorrect() {
    // Arrange and Act
    var topic = config.divergencesDlqTopic("reconciliation.divergences");

    // Assert
    assertThat(topic.name()).isEqualTo("reconciliation.divergences.dlq");
  }
}