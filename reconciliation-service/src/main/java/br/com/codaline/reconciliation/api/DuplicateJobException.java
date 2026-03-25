package br.com.codaline.reconciliation.api;

public class DuplicateJobException extends RuntimeException {

  private final String fileReference;

  public DuplicateJobException(String fileReference) {
    super("Job already executed for file: " + fileReference);
    this.fileReference = fileReference;
  }

  public String getFileReference() {
    return fileReference;
  }
}
