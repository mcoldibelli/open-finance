package br.com.codaline.gateway.audit;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public record AuditEvent(
    String eventType,
    LocalDateTime occurredAt,
    String source,
    String interactionId,
    String clientId,
    String consentId,
    Map<String, Object> details
) {

  public AuditEvent {
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}
