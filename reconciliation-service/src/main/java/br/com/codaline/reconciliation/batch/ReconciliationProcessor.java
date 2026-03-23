package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.LedgerTransactionRepository;
import br.com.codaline.reconciliation.domain.ReconciliationResult;
import br.com.codaline.reconciliation.domain.ReconciliationRun;
import br.com.codaline.reconciliation.domain.ReconciliationRunRepository;
import br.com.codaline.reconciliation.domain.ReconciliationStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProcessor implements
    ItemProcessor<CipTransaction, ReconciliationResult>,
    StepExecutionListener {

  private final LedgerTransactionRepository ledger;
  private final ReconciliationRunRepository runRepository;
  private int ispbStart;
  private int ispbEnd;

  private ReconciliationRun currentRun;

  public ReconciliationProcessor(LedgerTransactionRepository ledger,
      ReconciliationRunRepository runRepository) {
    this.ledger = ledger;
    this.runRepository = runRepository;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    Long runId = stepExecution.getJobExecution()
        .getExecutionContext()
        .getLong("runId");

    currentRun = runRepository.findById(runId)
        .orElseThrow(() -> new IllegalStateException(
            "ReconciliationRun not found for id: " + runId));

    ispbStart = stepExecution.getExecutionContext().getInt("ispbStart");
    ispbEnd = stepExecution.getExecutionContext().getInt("ispbEnd");
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    return stepExecution.getExitStatus();
  }

  @Override
  public ReconciliationResult process(CipTransaction item) throws Exception {
    int ispb = Integer.parseInt(item.debtorIspb().trim());
    if (ispb < ispbStart || ispb > ispbEnd) {
      return null;
    }

    var ledgerTx = ledger.findByEndToEndId(item.endToEndId());

    if (ledgerTx.isEmpty()) {
      return new ReconciliationResult(
          currentRun,
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
        currentRun,
        item.endToEndId(),
        item.debtorIspb(),
        item.creditorIspb(),
        item.amount(),
        ledgerAmount,
        status
    );
  }
}
