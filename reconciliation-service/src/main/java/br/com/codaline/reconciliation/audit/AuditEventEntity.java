package br.com.codaline.reconciliation.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String eventType;

  @Column(nullable = false)
  private LocalDateTime occurredAt;

  @Column(nullable = false, length = 30)
  private String source;

  @Column(length = 36)
  private String interactionId;

  @Column(length = 100)
  private String clientId;

  @Column(length = 100)
  private String consentId;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> details;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  protected AuditEventEntity() {
  }

  public static AuditEventEntity from(AuditEvent event) {
    var entity = new AuditEventEntity();
    entity.eventType = event.eventType();
    entity.occurredAt = event.occurredAt();
    entity.source = event.source();
    entity.interactionId = event.interactionId();
    entity.clientId = event.clientId();
    entity.consentId = event.consentId();
    entity.details = event.details();
    entity.createdAt = LocalDateTime.now();
    return entity;
  }

  public Long getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public String getSource() {
    return source;
  }

  public String getInteractionId() {
    return interactionId;
  }

  public String getClientId() {
    return clientId;
  }

  public String getConsentId() {
    return consentId;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
