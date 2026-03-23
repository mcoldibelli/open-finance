package br.com.codaline.reconciliation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRepository extends JpaRepository<ReconciliationResult, Long> {

}
