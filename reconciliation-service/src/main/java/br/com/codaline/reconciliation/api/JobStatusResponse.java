package br.com.codaline.reconciliation.api;

import br.com.codaline.reconciliation.domain.ReconciliationRun;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record JobStatusResponse(
    String fileReference,
    String status,
    LocalDate competenceDate,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Integer totalRecords,
    Integer matchedCount,
    Integer divergenceCount,
    Integer missingInLedgerCount,
    Integer missingInCipCount
) {

  public static JobStatusResponse from(ReconciliationRun run) {
    return new JobStatusResponse(
        run.getFileReference(),
        run.getStatus().name(),
        run.getCompetenceDate(),
        run.getStartedAt(),
        run.getFinishedAt(),
        run.getTotalRecords(),
        run.getMatchedCount(),
        run.getDivergenceCount(),
        run.getMissingInLedgerCount(),
        run.getMissingInCipCount()
    );
  }
}
