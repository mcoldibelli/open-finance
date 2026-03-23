package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.LedgerTransactionRepository;
import br.com.codaline.reconciliation.domain.ReconciliationResult;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProcessor implements
    ItemProcessor<CipTransaction, ReconciliationResult> {

  private final LedgerTransactionRepository ledger;
  private final ReconciliationRunRepository reconciliation;

  public ReconciliationProcessor(LedgerTransactionRepository ledger,
      ReconciliationRunRepository reconciliation) {
    this.ledger = ledger;
    this.reconciliation = reconciliation;
  }

  @Override
  public ReconciliationResult process(CipTransaction item) throws Exception {
    var ledgerTx = ledger.findByEndToEndId(item.endToEndId());

    if (ledgerTx.isEmpty()) {
      return new ReconciliationResult(
          null,
          item.endToEndId(),
          item.debtorIspb(),
          item.creditorIspb(),
          item.amount(),
          null,
          ReconciliationStatus.MISSING_IN_LEDGER);
    }

    var ledgerAmount = ledgerTx.get().getAmount();
    var status = item.amount().compareTo(ledgerAmount) == 0
        ? ReconciliationStatus.MATCHED
        : ReconciliationStatus.AMOUNT_DIVERGENCE;

    return new ReconciliationResult(
        null,
        item.endToEndId(),
        item.debtorIspb(),
        item.creditorIspb(),
        item.amount(),
        ledgerAmount,
        status
    );
  }
}
