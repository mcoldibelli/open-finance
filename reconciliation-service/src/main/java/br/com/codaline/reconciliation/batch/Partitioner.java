package br.com.codaline.reconciliation.batch;

import java.util.Map;
import org.hibernate.sql.exec.spi.ExecutionContext;

public interface Partitioner {

  Map<String, ExecutionContext> partition(int gridSize);
}
