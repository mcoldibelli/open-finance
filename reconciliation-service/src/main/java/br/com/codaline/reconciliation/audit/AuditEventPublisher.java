package br.com.codaline.reconciliation.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

  private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
  private final String topic;

  public AuditEventPublisher(KafkaTemplate<String, AuditEvent> kafkaTemplate,
      @Value("${kafka.topics.audit}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public void publish(AuditEvent event) {
    try {
      kafkaTemplate.send(topic, event.interactionId(), event)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("Failed to publish audit event: {}", event.eventType(), ex);
            }
          });
    } catch (Exception e) {
      log.error("Failed to publish audit event: {}", event.eventType(), e);
    }
  }
}
