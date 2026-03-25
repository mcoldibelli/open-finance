package br.com.codaline.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.codaline.reconciliation.batch.CipTransaction;
import br.com.codaline.reconciliation.batch.CipTransactionFieldSetMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FieldSet;

class CipTransactionFieldSetMapperTest {

  private static final String[] FIELD_NAMES = {"endToEndId", "debtorIspb", "creditorIspb", "amount"};

  private final CipTransactionFieldSetMapper mapper = new CipTransactionFieldSetMapper();

  @Test
  void given_validFieldSet_when_map_then_convertsAmountToReais() throws Exception {
    FieldSet fieldSet = new DefaultFieldSet(
        new String[]{"E0000000100000123456", "ISPB0001", "ISPB0002", "0000000000010050"},
        FIELD_NAMES);

    CipTransaction result = mapper.mapFieldSet(fieldSet);

    assertThat(result.endToEndId()).isEqualTo("E0000000100000123456");
    assertThat(result.debtorIspb()).isEqualTo("ISPB0001");
    assertThat(result.creditorIspb()).isEqualTo("ISPB0002");
    assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("100.50"));
  }

  @Test
  void given_emptyAmount_when_map_then_throwsException() {
    FieldSet fieldSet = new DefaultFieldSet(
        new String[]{"E0000000100000123456", "ISPB0001", "ISPB0002", "   "},
        FIELD_NAMES);

    assertThatThrownBy(() -> mapper.mapFieldSet(fieldSet))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
  }

  @Test
  void given_amountWithNonNumericChars_when_map_then_throwsException() {
    FieldSet fieldSet = new DefaultFieldSet(
        new String[]{"E0000000100000123456", "ISPB0001", "ISPB0002", "ABC12345"},
        FIELD_NAMES);

    assertThatThrownBy(() -> mapper.mapFieldSet(fieldSet))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
  }
}
