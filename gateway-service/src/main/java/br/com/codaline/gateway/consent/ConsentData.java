package br.com.codaline.gateway.consent;

import java.util.Set;

public class ConsentData {

  private String consentId;
  private ConsentStatus status;
  private Set<String> permissions;
  private String linkedCpf;
  private String clientId;

  public ConsentData() {
  }

  public ConsentData(String consentId, ConsentStatus status, Set<String> permissions,
      String linkedCpf, String clientId) {
    this.consentId = consentId;
    this.status = status;
    this.permissions = permissions;
    this.linkedCpf = linkedCpf;
    this.clientId = clientId;
  }

  public String getConsentId() {
    return consentId;
  }

  public ConsentStatus getStatus() {
    return status;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public String getLinkedCpf() {
    return linkedCpf;
  }

  public String getClientId() {
    return clientId;
  }
}
