package br.com.codaline.reconciliation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("reconciliation")
          .withUsername("reconciliation")
          .withPassword("test-password");

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

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
  }
}
