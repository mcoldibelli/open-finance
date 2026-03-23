package br.com.codaline.reconciliation.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

  Optional<ReconciliationRun> findByFileReference(String fileReference);
}
