package br.com.codaline.reconciliation.batch;

public class KafkaPublishException extends RuntimeException {

  public KafkaPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
