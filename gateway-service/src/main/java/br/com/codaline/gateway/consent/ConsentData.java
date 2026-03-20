package br.com.codaline.gateway.consent;

import java.util.Set;

public record ConsentData(
    String consentId,
    ConsentStatus status,
    Set<String> permissions,
    String linkedCpf,
    String clientId
) {

  public ConsentData {
    permissions = permissions != null ? Set.copyOf(permissions) : Set.of();
  }
}
