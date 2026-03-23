package br.com.codaline.reconciliation.domain;

public enum ReconciliationStatus {
  MATCHED,
  AMOUNT_DIVERGENCE,
  MISSING_IN_LEDGER,
  MISSING_IN_CIP
}
