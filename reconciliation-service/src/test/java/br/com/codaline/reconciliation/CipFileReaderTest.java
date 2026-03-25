package br.com.codaline.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.codaline.reconciliation.batch.CipFileReaderConfig;
import br.com.codaline.reconciliation.batch.CipTransaction;
import br.com.codaline.reconciliation.batch.CnabFileBuilder;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;

class CipFileReaderTest {

  @TempDir
  Path tempDir;

  @Test
  void given_fileWithTwoTransactions_when_read_then_returnsBothWithCorrectValues()
      throws Exception {
    Path file = tempDir.resolve("test.txt");
    String content = CnabFileBuilder.buildHeaderLine() + "\n"
        + CnabFileBuilder.buildSegmentALine("E0000000100000123456", "ISPB0001", "ISPB0002",
        "0000000000010050") + "\n"
        + CnabFileBuilder.buildSegmentALine("E0000000200000654321", "ISPB0003", "ISPB0004",
        "0000000000025000") + "\n"
        + CnabFileBuilder.buildHeaderLine() + "\n";
    Files.writeString(file, content);

    List<CipTransaction> result = readAll(file);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().endToEndId()).isEqualTo("E0000000100000123456");
    assertThat(result.getFirst().debtorIspb()).isEqualTo("ISPB0001");
    assertThat(result.getFirst().creditorIspb()).isEqualTo("ISPB0002");
    assertThat(result.getFirst().amount()).isEqualByComparingTo(new BigDecimal("100.50"));
    assertThat(result.get(1).endToEndId()).isEqualTo("E0000000200000654321");
    assertThat(result.get(1).debtorIspb()).isEqualTo("ISPB0003");
    assertThat(result.get(1).creditorIspb()).isEqualTo("ISPB0004");
    assertThat(result.get(1).amount()).isEqualByComparingTo(new BigDecimal("250.00"));
  }

  @Test
  void given_fileWithOnlyHeader_when_read_then_returnsEmptyList() throws Exception {
    Path file = tempDir.resolve("empty.txt");
    Files.writeString(file, CnabFileBuilder.buildHeaderLine() + "\n");

    List<CipTransaction> result = readAll(file);

    assertThat(result).isEmpty();
  }

  @Test
  void given_transactionWithZeroAmount_when_read_then_returnsBigDecimalZero() throws Exception {
    Path file = tempDir.resolve("zero.txt");
    String content = CnabFileBuilder.buildHeaderLine() + "\n"
        + CnabFileBuilder.buildSegmentALine("E0000000100000000000", "ISPB0001", "ISPB0002",
        "0000000000000000") + "\n";
    Files.writeString(file, content);

    List<CipTransaction> result = readAll(file);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().amount()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private List<CipTransaction> readAll(Path file) throws Exception {
    CipFileReaderConfig config = new CipFileReaderConfig();
    FlatFileItemReader<CipTransaction> reader = config.cipFileReader(
        file.getParent().toString(), file.getFileName().toString());
    reader.open(new ExecutionContext());
    try {
      List<CipTransaction> items = new ArrayList<>();
      CipTransaction item;
      while ((item = reader.read()) != null) {
        items.add(item);
      }
      return items;
    } finally {
      reader.close();
    }
  }
}
