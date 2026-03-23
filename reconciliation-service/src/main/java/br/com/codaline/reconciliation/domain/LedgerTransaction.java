package br.com.codaline.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ledger_transactions",
    indexes = {
        @Index(name = "idx_ledger_end2end", columnList = "end_to_end_id", unique = true)
    })
public class LedgerTransaction {


  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String endToEndId;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String debtorIspb;

  @Column(nullable = false, length = 8)
  private String creditorIspb;

  @Column(nullable = false)
  private LocalDate transactionDate;

  public LedgerTransaction() {
  }

  public Long getId() {
    return id;
  }

  public String getEndToEndId() {
    return endToEndId;
  }

  public void setEndToEndId(String endToEndId) {
    this.endToEndId = endToEndId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getDebtorIspb() {
    return debtorIspb;
  }

  public void setDebtorIspb(String debtorIspb) {
    this.debtorIspb = debtorIspb;
  }

  public String getCreditorIspb() {
    return creditorIspb;
  }

  public void setCreditorIspb(String creditorIspb) {
    this.creditorIspb = creditorIspb;
  }

  public LocalDate getTransactinDate() {
    return transactionDate;
  }

  public void setTransactinDate(LocalDate transactionDate) {
    this.transactionDate = transactionDate;
  }
}
