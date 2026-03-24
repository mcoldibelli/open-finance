package br.com.codaline.reconciliation.api;

import java.time.LocalDateTime;

public record JobLaunchResponse(
    String fileReference,
    String status,
    LocalDateTime acceptedAt
) {
  public static JobLaunchResponse accepted(String fileReference) {
    return new JobLaunchResponse(fileReference, "ACCEPTED", LocalDateTime.now());
  }
}
