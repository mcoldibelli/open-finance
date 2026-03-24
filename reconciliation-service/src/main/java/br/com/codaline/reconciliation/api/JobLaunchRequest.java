package br.com.codaline.reconciliation.api;

import jakarta.validation.constraints.NotBlank;

public record JobLaunchRequest(@NotBlank String fileReference) {
}
