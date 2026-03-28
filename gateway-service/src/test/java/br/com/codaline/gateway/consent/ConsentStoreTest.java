package br.com.codaline.gateway.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ConsentStoreTest {

  private final ConsentStore consentStore = new ConsentStore(null);

  @Test
  void dado_permissoesVazias_quando_toConsentData_entao_retornaMonoEmpty() {
    // Arrange
    var fields = Map.of(
        "consent_id", "urn:banco:consent:abc123",
        "status", "AUTHORISED",
        "permissions", "",
        "cpf", "12345678900",
        "client_id", "client-1"
    );

    // Act & Assert
    StepVerifier.create(consentStore.toConsentData(fields))
        .verifyComplete();
  }

  @Test
  void dado_permissoesValidas_quando_toConsentData_entao_retornaConsentData() {
    // Arrange
    var fields = Map.of(
        "consent_id", "urn:banco:consent:abc123",
        "status", "AUTHORISED",
        "permissions", "ACCOUNTS_READ,ACCOUNTS_BALANCES_READ",
        "cpf", "12345678900",
        "client_id", "client-1"
    );

    // Act & Assert
    StepVerifier.create(consentStore.toConsentData(fields))
        .assertNext(consent -> {
          assertThat(consent.consentId()).isEqualTo("urn:banco:consent:abc123");
          assertThat(consent.status()).isEqualTo(ConsentStatus.AUTHORISED);
          assertThat(consent.permissions()).containsExactlyInAnyOrder(
              "ACCOUNTS_READ", "ACCOUNTS_BALANCES_READ");
        })
        .verifyComplete();
  }

  @Test
  void dado_statusNull_quando_toConsentData_entao_retornaMonoEmpty() {
    // Arrange
    var fields = Map.of(
        "consent_id", "urn:banco:consent:abc123",
        "permissions", "ACCOUNTS_READ",
        "cpf", "12345678900",
        "client_id", "client-1"
    );

    // Act & Assert
    StepVerifier.create(consentStore.toConsentData(fields))
        .verifyComplete();
  }
}
