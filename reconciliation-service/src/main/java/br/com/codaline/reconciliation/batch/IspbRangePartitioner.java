package br.com.codaline.reconciliation.batch;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class IspbRangePartitioner implements Partitioner {

  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    Map<String, ExecutionContext> partitions = new HashMap<>();

    int total = 10_000;
    int size = total / gridSize;

    for (int i = 0; i < gridSize; i++) {
      int start = i * size;
      int end = (i == gridSize - 1) ? total - 1 : (i + 1) * size - 1;

      ExecutionContext ctx = new ExecutionContext();
      ctx.putInt("ispbStart", start);
      ctx.putInt("ispbEnd", end);
      partitions.put("partition" + i, ctx);
    }

    return partitions;
  }
}
