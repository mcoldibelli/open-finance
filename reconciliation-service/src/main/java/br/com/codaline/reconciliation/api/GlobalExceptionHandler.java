package br.com.codaline.reconciliation.api;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(DuplicateJobException.class)
  public ProblemDetail handleDuplicateJob(DuplicateJobException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Duplicate job");
    problem.setType(URI.create("about:blank"));
    problem.setProperty("fileReference", ex.getFileReference());
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGenericException(Exception ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal error while processing request");
    problem.setTitle("Internal error");
    problem.setType(URI.create("about:blank"));
    return problem;
  }
}
