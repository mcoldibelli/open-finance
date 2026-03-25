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

    line[7] = '3'; // record type: 3 (position 008)
    line[8] = 'A'; // segment: A (position 009)

    writeField(line, 72, endToEndId, 20); // endToEndId - positions 073-092 (indices 72-91)
    writeField(line, 92, debtorIspb, 8); // debtorIspb - positions 093-100 (indices 92-99)
    writeField(line, 100, creditorIspb, 8); // creditorIspb - positions 101-108 (indices 100-107)
    writeField(line, 152, amountCents, 16); // amount - positions 153-168 (indices 152-167)

    return new String(line);
  }

  public static String buildHeaderLine() {
    char[] line = new char[LINE_LENGTH];
    Arrays.fill(line, ' ');
    line[7] = '0'; // type: file header
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
