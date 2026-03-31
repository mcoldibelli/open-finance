package br.com.codaline.reconciliation.audit;

import org.springframework.data.repository.Repository;

public interface AuditRepository extends Repository<AuditEventEntity, Long> {

  AuditEventEntity save(AuditEventEntity entity);
}
