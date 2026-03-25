package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DivergenceKafkaWriter implements ItemWriter<ReconciliationResult> {

  private static final Logger log = LoggerFactory.getLogger(DivergenceKafkaWriter.class);
  private static final int SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, ReconciliationResult> kafkaTemplate;
  private final String topic;

  public DivergenceKafkaWriter(KafkaTemplate<String, ReconciliationResult> kafkaTemplate,
      @Value("${kafka.topics.divergences:reconciliation.divergences}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Override
  public void write(@NonNull Chunk<? extends ReconciliationResult> chunk) {
    chunk.getItems().stream()
        .filter(r -> r.getStatus() != ReconciliationStatus.MATCHED)
        .forEach(this::sendDivergence);
  }

  private void sendDivergence(ReconciliationResult result) {
    try {
      kafkaTemplate.send(topic, result.getEndToEndId(), result)
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new KafkaPublishException(
          "Failed to publish divergence to Kafka: endToEndId=" + result.getEndToEndId(), ex);
    }
  }
}
