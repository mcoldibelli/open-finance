package br.com.codaline.reconciliation.batch;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.mapping.PatternMatchingCompositeLineMapper;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.LineTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

@Configuration
public class CipFileReaderConfig {

  private static final String SEGMENT_A_PATTERN = "???????3A*";

  private static final Range END_TO_END_ID = new Range(73, 92);
  private static final Range DEBTOR_ISPB = new Range(93, 100);
  private static final Range CREDITOR_ISPB = new Range(101, 108);
  private static final Range AMOUNT = new Range(153, 168);

  @Bean
  @StepScope
  public FlatFileItemReader<CipTransaction> cipFileReader(
      @Value("${reconciliation.files.input-path}") String inputPath) {

    FixedLengthTokenizer segmentA = new FixedLengthTokenizer();
    segmentA.setNames("endToEndId", "debtorIspb", "creditorIspb", "amount");
    segmentA.setStrict(false);
    segmentA.setColumns(END_TO_END_ID, DEBTOR_ISPB, CREDITOR_ISPB, AMOUNT);

    PatternMatchingCompositeLineMapper<CipTransaction> lineMapper = new PatternMatchingCompositeLineMapper<>();

    Map<String, LineTokenizer> tokenizers = new HashMap<>();
    tokenizers.put(SEGMENT_A_PATTERN, segmentA);
    tokenizers.put("*", line -> new DefaultFieldSet(new String[]{line}));
    lineMapper.setTokenizers(tokenizers);

    // Linhas fora do segmento A (header, trailer) retornam null — ignoradas pelo Spring Batch
    Map<String, FieldSetMapper<CipTransaction>> mappers = new HashMap<>();
    mappers.put(SEGMENT_A_PATTERN, new CipTransactionFieldSetMapper());
    mappers.put("*", fieldSet -> null);
    lineMapper.setFieldSetMappers(mappers);

    FlatFileItemReader<CipTransaction> reader = new FlatFileItemReader<>();
    reader.setLineMapper(lineMapper);
    reader.setResource(new FileSystemResource(inputPath));
    reader.setLinesToSkip(1);

    return reader;
  }
}
