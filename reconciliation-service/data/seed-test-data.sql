-- Seed de teste para o reconciliation-service.
-- Usar junto com o arquivo CIP_20260401.txt (3 transacoes).
--
-- Cenarios cobertos:
--   E0000000100000000001  100.50 no CIP, 100.50 no ledger  -> MATCHED
--   E0000000200000000002  150.00 no CIP, 149.99 no ledger  -> AMOUNT_DIVERGENCE
--   E0000000300000000003  750.00 no CIP, ausente no ledger  -> MISSING_IN_LEDGER
--
-- Rodar:
--   docker exec -i postgres-openfinance psql -U reconciliation -d reconciliation < reconciliation-service/data/seed-test-data.sql

DELETE FROM reconciliation_results;
DELETE FROM staging_cip_transactions;
DELETE FROM ledger_transactions;

INSERT INTO ledger_transactions (end_to_end_id, amount, debtor_ispb, creditor_ispb, transaction_date) VALUES
  ('E0000000100000000001', 100.50, '00000100', '00000200', '2026-04-01'),
  ('E0000000200000000002', 149.99, '00000200', '00000300', '2026-04-01');
-- E0000000300000000003 ausente no ledger (proposital)
