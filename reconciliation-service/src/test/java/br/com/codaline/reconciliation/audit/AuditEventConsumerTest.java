package br.com.codaline.reconciliation.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

  @Mock
  private AuditRepository auditRepository;

  @InjectMocks
  private AuditEventConsumer consumer;

  @Test
  void given_auditEvent_when_consume_then_persistsEntityWithCorrectFields() {
    // Arrange
    var occurredAt = LocalDateTime.of(2026, 3, 31, 10, 0);
    var event = new AuditEvent(
        "MTLS_VALIDATION_SUCCESS", occurredAt, "gateway-service",
        "interaction-1", "client-1", "consent-1",
        Map.of("certThumbprint", "abc")
    );

    // Act
    consumer.consume(event);

    // Assert
    var captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(auditRepository).save(captor.capture());

    var entity = captor.getValue();
    assertThat(entity.getEventType()).isEqualTo("MTLS_VALIDATION_SUCCESS");
    assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(entity.getSource()).isEqualTo("gateway-service");
    assertThat(entity.getInteractionId()).isEqualTo("interaction-1");
    assertThat(entity.getClientId()).isEqualTo("client-1");
    assertThat(entity.getConsentId()).isEqualTo("consent-1");
    assertThat(entity.getDetails()).isEqualTo(Map.of("certThumbprint", "abc"));
    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
