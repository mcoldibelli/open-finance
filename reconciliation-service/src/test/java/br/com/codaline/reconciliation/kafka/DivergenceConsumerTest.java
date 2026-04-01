package br.com.codaline.reconciliation.kafka;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import br.com.codaline.reconciliation.domain.ReconciliationResult;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DivergenceConsumerTest {

  private final DivergenceConsumer consumer = new DivergenceConsumer();

  @Test
  void given_amountDivergence_when_consume_then_processWithoutError() {
    // Arrange
    var result = new ReconciliationResult(
        null, "E2E20260401T100000", "12345678", "87654321",
        new BigDecimal("1500.00"), new BigDecimal("1499.50"),
        ReconciliationStatus.AMOUNT_DIVERGENCE);

    // Act and Assert
    assertThatCode(() -> consumer.consume(result)).doesNotThrowAnyException();
  }

  @Test
  void given_missingInLedger_when_consume_then_processesWithoutError() {
    // Arrange
    var result = new ReconciliationResult(null, "E2E20260401T100000", "12345678", "87654321",
        new BigDecimal("2000.00"), null, ReconciliationStatus.MISSING_IN_LEDGER);

    // Act and Assert
    assertThatCode(() -> consumer.consume(result)).doesNotThrowAnyException();
  }
}