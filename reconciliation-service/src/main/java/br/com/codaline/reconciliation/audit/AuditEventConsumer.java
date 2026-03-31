package br.com.codaline.reconciliation.audit;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

  private final AuditRepository auditRepository;

  public AuditEventConsumer(AuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  @KafkaListener(topics = "${kafka.topics.audit}", groupId = "audit-consumer")
  public void consume(AuditEvent event) {
    auditRepository.save(AuditEventEntity.from(event));
  }
}
