package br.com.codaline.reconciliation.batch;

import br.com.codaline.reconciliation.domain.ReconciliationResultRepository;
import br.com.codaline.reconciliation.domain.ReconciliationResult;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationResultWriter implements ItemWriter<ReconciliationResult> {

  private final ReconciliationResultRepository repository;

  public ReconciliationResultWriter(ReconciliationResultRepository repository) {
    this.repository = repository;
  }

  @Override
  public void write(@NonNull Chunk<? extends ReconciliationResult> chunk) {
    repository.saveAll(chunk.getItems());
  }
}
