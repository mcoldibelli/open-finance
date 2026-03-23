package br.com.codaline.reconciliation.batch;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class IspbRangePartitioner implements Partitioner {

  private static final int ISPB_TOTAL = 10_000;

  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    if (gridSize <= 0) {
      throw new IllegalArgumentException("gridSize must be positive, got: " + gridSize);
    }

    Map<String, ExecutionContext> partitions = new HashMap<>();
    int size = ISPB_TOTAL / gridSize;

    for (int i = 0; i < gridSize; i++) {
      int start = i * size;
      int end = (i == gridSize - 1) ? ISPB_TOTAL - 1 : (i + 1) * size - 1;

      ExecutionContext ctx = new ExecutionContext();
      ctx.putInt("ispbStart", start);
      ctx.putInt("ispbEnd", end);
      partitions.put("partition" + i, ctx);
    }

    return partitions;
  }
}
