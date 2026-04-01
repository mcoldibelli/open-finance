package br.com.codaline.reconciliation.kafka;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DivergenceConsumer {

  private static final Logger log = LoggerFactory.getLogger(DivergenceConsumer.class);

  @KafkaListener(
      topics = "${kafka.topics.divergences}",
      groupId = "divergence-consumer",
      containerFactory = "divergenceListenerContainerFactory")
  public void consume(ReconciliationResult result) {
    log.info("Divergence received: endToEnd={}, status={}, cipAmount={}, ledgerAmount={}",
        result.getEndToEndId(), result.getStatus(), result.getCipAmount(),
        result.getLedgerAmount());
  }
}
