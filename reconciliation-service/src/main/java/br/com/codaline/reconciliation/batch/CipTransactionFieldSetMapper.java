package br.com.codaline.reconciliation.batch;

import java.math.BigDecimal;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

public class CipTransactionFieldSetMapper implements FieldSetMapper<CipTransaction> {

  @Override
  public CipTransaction mapFieldSet(FieldSet fieldSet) throws BindException {
    String rawAmount = fieldSet.readString("amount").trim();

    if (rawAmount.isEmpty() || !rawAmount.matches("\\d+")) {
      throw new IllegalArgumentException(
          "Invalid 'amount' field: '" + rawAmount + "'. Expected: numeric value representing cents");
    }

    return new CipTransaction(
        fieldSet.readString("endToEndId").trim(),
        fieldSet.readString("debtorIspb").trim(),
        fieldSet.readString("creditorIspb").trim(),
        new BigDecimal(rawAmount).movePointLeft(2)
    );
  }
}
