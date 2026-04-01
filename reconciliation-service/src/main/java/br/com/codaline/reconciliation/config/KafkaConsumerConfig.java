package br.com.codaline.reconciliation.config;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  @Bean("divergenceListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, ReconciliationResult> divergenceListenerContainerFactory(
      KafkaProperties kafkaProperties,
      KafkaOperations<String, ReconciliationResult> kafkaTemplate,
      @Value("${kafka.topics.divergences}") String divergencesTopic
  ) {

    var consumerProps = kafkaProperties.buildConsumerProperties(null);
    consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ReconciliationResult.class.getName());
    consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "br.com.codaline.reconciliation.domain");

    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(divergencesTopic + ".dlq", -1));
    var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2));

    var factory = new ConcurrentKafkaListenerContainerFactory<String, ReconciliationResult>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProps));
    factory.setCommonErrorHandler(errorHandler);

    return factory;
  }

}
