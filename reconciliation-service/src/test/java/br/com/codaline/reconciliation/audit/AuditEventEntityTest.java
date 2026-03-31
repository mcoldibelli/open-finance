package br.com.codaline.reconciliation.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventEntityTest {

  @Test
  void given_auditEvent_when_from_then_mapsAllFields() {
    // Arrange
    var occurredAt = LocalDateTime.of(2026, 3, 31, 10, 0);
    var details = Map.<String, Object>of("key", "value");
    var event = new AuditEvent(
        "TEST_EVENT", occurredAt, "test-service",
        "interaction-1", "client-1", "consent-1", details
    );

    // Act
    var entity = AuditEventEntity.from(event);

    // Assert
    assertThat(entity.getEventType()).isEqualTo("TEST_EVENT");
    assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(entity.getSource()).isEqualTo("test-service");
    assertThat(entity.getInteractionId()).isEqualTo("interaction-1");
    assertThat(entity.getClientId()).isEqualTo("client-1");
    assertThat(entity.getConsentId()).isEqualTo("consent-1");
    assertThat(entity.getDetails()).isEqualTo(details);
    assertThat(entity.getId()).isNull();
  }

  @Test
  void given_auditEvent_when_from_then_setsCreatedAtToNow() {
    // Arrange
    var before = LocalDateTime.now();
    var event = new AuditEvent(
        "TEST_EVENT", LocalDateTime.now(), "test-service",
        null, null, null, null
    );

    // Act
    var entity = AuditEventEntity.from(event);

    // Assert
    var after = LocalDateTime.now();
    assertThat(entity.getCreatedAt()).isBetween(before, after);
  }
}
