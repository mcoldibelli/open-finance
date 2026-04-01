package br.com.codaline.reconciliation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

  @SuppressWarnings("resource")
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("reconciliation")
          .withUsername("reconciliation")
          .withPassword("test-password");

  @Container
  static final ConfluentKafkaContainer kafka =
      new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.producer.key-serializer",
        () -> "org.apache.kafka.common.serialization.StringSerializer");
    registry.add("spring.kafka.producer.value-serializer",
        () -> "org.springframework.kafka.support.serializer.JsonSerializer");
    registry.add("spring.batch.jdbc.initialize-schema", () -> "always");
    registry.add("spring.batch.job.enabled", () -> "false");
    registry.add("spring.config.import", () -> "");
    registry.add("kafka.topics.audit", () -> "test-audit");
    registry.add("kafka.topics.divergences", () -> "test-divergences");
    registry.add("spring.kafka.consumer.group-id", () -> "test-audit-consumer");
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add("spring.kafka.consumer.key-deserializer",
        () -> "org.apache.kafka.common.serialization.StringDeserializer");
    registry.add("spring.kafka.consumer.value-deserializer",
        () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
    registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages",
        () -> "br.com.codaline.reconciliation.audit");
    registry.add("spring.kafka.consumer.properties.spring.json.use.type.headers",
        () -> "false");
    registry.add("spring.kafka.consumer.properties.spring.json.value.default.type",
        () -> "br.com.codaline.reconciliation.audit.AuditEvent");
  }
}
