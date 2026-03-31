package br.com.codaline.gateway.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

  @Mock
  private KafkaTemplate<String, AuditEvent> kafkaTemplate;

  private AuditEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new AuditEventPublisher(kafkaTemplate, "audit.events");
  }

  @Test
  void given_validEvent_when_publish_then_sendsToCorrectTopicAndKey() {
    // Arrange
    var event = new AuditEvent(
        "MTLS_VALIDATION_SUCCESS", LocalDateTime.now(), "gateway-service",
        "interaction-123", "client-1", "consent-1",
        Map.of("certThumbprint", "abc")
    );
    when(kafkaTemplate.send("audit.events", "interaction-123", event))
        .thenReturn(CompletableFuture.<SendResult<String, AuditEvent>>completedFuture(null));

    // Act
    publisher.publish(event);

    // Assert
    verify(kafkaTemplate).send("audit.events", "interaction-123", event);
  }

  @Test
  void given_kafkaSyncFailure_when_publish_then_doesNotThrow() {
    // Arrange
    var event = new AuditEvent(
        "MTLS_VALIDATION_FAILURE", LocalDateTime.now(), "gateway-service",
        null, null, null,
        Map.of("reason", "test failure")
    );
    when(kafkaTemplate.send(any(), any(), any()))
        .thenThrow(new RuntimeException("Kafka down"));

    // Act — should not throw
    publisher.publish(event);

    // Assert
    verify(kafkaTemplate).send("audit.events", null, event);
  }
}
