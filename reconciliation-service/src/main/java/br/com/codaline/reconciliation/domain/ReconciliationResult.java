package br.com.codaline.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_results",
    indexes = {
        @Index(name = "idx_result_run", columnList = "run_id"),
        @Index(name = "idx_result_status", columnList = "status"),
        @Index(name = "idx_result_end2end", columnList = "end_to_end_id")
    })
public class ReconciliationResult {


  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_id", nullable = false)
  private ReconciliationRun run;

  @Column(nullable = false)
  private String endToEndId;

  @Column(nullable = false, length = 8)
  private String debtorIspb;

  @Column(nullable = false, length = 8)
  private String creditorIspb;

  private BigDecimal cipAmount;
  private BigDecimal ledgerAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReconciliationStatus status;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public ReconciliationResult() {
  }

  public ReconciliationResult(ReconciliationRun run, String endToEndId, String debtorIspb,
      String creditorIspb, BigDecimal cipAmount, BigDecimal ledgerAmount,
      ReconciliationStatus status) {
    this.run = run;
    this.endToEndId = endToEndId;
    this.debtorIspb = debtorIspb;
    this.creditorIspb = creditorIspb;
    this.cipAmount = cipAmount;
    this.ledgerAmount = ledgerAmount;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public ReconciliationRun getRun() {
    return run;
  }

  public String getEndToEndId() {
    return endToEndId;
  }

  public String getDebtorIspb() {
    return debtorIspb;
  }

  public String getCreditorIspb() {
    return creditorIspb;
  }

  public BigDecimal getCipAmount() {
    return cipAmount;
  }

  public BigDecimal getLedgerAmount() {
    return ledgerAmount;
  }

  public ReconciliationStatus getStatus() {
    return status;
  }

  public LocalDateTime getProcessedAt() {
    return processedAt;
  }
}
