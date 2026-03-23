package br.com.codaline.reconciliation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

  long countByRunAndStatus(ReconciliationRun run, ReconciliationStatus status);
}
