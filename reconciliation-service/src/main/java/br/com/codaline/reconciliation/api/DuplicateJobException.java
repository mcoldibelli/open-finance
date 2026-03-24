package br.com.codaline.reconciliation.api;

public class DuplicateJobException extends RuntimeException {

  private final String fileReference;

  public DuplicateJobException(String fileReference) {
    super("Job já executado para o arquivo: " + fileReference);
    this.fileReference = fileReference;
  }

  public String getFileReference() {
    return fileReference;
  }
}
