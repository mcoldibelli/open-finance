package br.com.codaline.reconciliation.batch;

import java.util.Arrays;

public class CnabFileBuilder {

  private static final int LINE_LENGTH = 240;

  private CnabFileBuilder() {
  }

  public static String buildSegmentALine(
      String endToEndId,
      String debtorIspb,
      String creditorIspb,
      String amountCents
  ) {
    char[] line = new char[LINE_LENGTH];
    Arrays.fill(line, ' ');

    line[7] = '3'; // Tipo de registro: 3 (posição 008)
    line[8] = 'A'; // Segmento: A (posição 009)

    writeField(line, 72, endToEndId, 20); // endToEndId - posições 073-092 (índices 72-91)
    writeField(line, 92, debtorIspb, 8); // debtorIspb - posições 093-100 (índices 92-99)
    writeField(line, 100, creditorIspb, 8); // creditorIspb - posições 101-108 (índices 100-107)
    writeField(line, 152, amountCents, 16); // amount - posições 153-168 (índices 152-167)

    return new String(line);
  }

  public static String buildHeaderLine() {
    char[] line = new char[LINE_LENGTH];
    Arrays.fill(line, ' ');
    line[7] = '0'; // tipo: header de arquivo
    return new String(line);
  }

  private static void writeField(char[] line, int start, String value, int length) {
    String padded = value.length() >= length
        ? value.substring(0, length)
        : value + " ".repeat(length - value.length());

    for (int i = 0; i < length; i++) {
      line[start + i] = padded.charAt(i);
    }
  }
}
