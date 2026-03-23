package br.com.codaline.reconciliation.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

  Optional<LedgerTransaction> findByEndToEndId(String endToEndId);
}
