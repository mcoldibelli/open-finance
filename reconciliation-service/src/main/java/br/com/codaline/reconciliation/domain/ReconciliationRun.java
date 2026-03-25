package br.com.codaline.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String fileReference;

  @Column(nullable = false)
  private LocalDate competenceDate;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  private LocalDateTime finishedAt;
  private Integer totalRecords;
  private Integer matchedCount;
  private Integer divergenceCount;
  private Integer missingInLedgerCount;
  private Integer missingInCipCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunStatus status;

  public ReconciliationRun() {
  }

  public ReconciliationRun(String fileReference, LocalDate competenceDate) {
    this.fileReference = fileReference;
    this.competenceDate = competenceDate;
    this.startedAt = LocalDateTime.now();
    this.status = RunStatus.RUNNING;
  }

  public Long getId() {
    return id;
  }

  public String getFileReference() {
    return fileReference;
  }

  public LocalDate getCompetenceDate() {
    return competenceDate;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(LocalDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }

  public void setTotalRecords(Integer totalRecords) {
    this.totalRecords = totalRecords;
  }

  public Integer getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(Integer matchedCount) {
    this.matchedCount = matchedCount;
  }

  public Integer getDivergenceCount() {
    return divergenceCount;
  }

  public void setDivergenceCount(Integer divergenceCount) {
    this.divergenceCount = divergenceCount;
  }

  public Integer getMissingInLedgerCount() {
    return missingInLedgerCount;
  }

  public void setMissingInLedgerCount(Integer missingInLedgerCount) {
    this.missingInLedgerCount = missingInLedgerCount;
  }

  public Integer getMissingInCipCount() {
    return missingInCipCount;
  }

  public void setMissingInCipCount(Integer missingInCipCount) {
    this.missingInCipCount = missingInCipCount;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public enum RunStatus {
    RUNNING, COMPLETED, FAILED, SKIPPED_DUPLICATE
  }
}
