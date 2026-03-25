package br.com.codaline.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.codaline.gateway.config.TestJwtConfig;
import br.com.codaline.gateway.consent.ConsentData;
import br.com.codaline.gateway.consent.ConsentStatus;
import br.com.codaline.gateway.consent.ConsentStore;
import br.com.codaline.gateway.utils.TestUtils;
import com.nimbusds.jose.JOSEException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "server.ssl.enabled=false",
    "spring.cloud.gateway.routes[0].id=accounts-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8081",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/open-banking/accounts/v2/**",
    "spring.cloud.gateway.routes[0].filters[0]=FapiMtlsValidation",
    "spring.cloud.gateway.routes[0].filters[1]=JtiReplay",
    "spring.cloud.gateway.routes[0].filters[2]=ConsentValidation"
})
@Import(TestJwtConfig.class)
class JtiReplayFilterTest extends IntegrationTestBase {

  @Autowired
  private WebTestClient webTestClient;

  @BeforeAll
  static void setup(@Autowired ConsentStore consentStore) {
    ConsentData consent = new ConsentData(
        "consent-filter-test",
        ConsentStatus.AUTHORISED,
        Set.of("ACCOUNTS_READ", "ACCOUNTS_BALANCES_READ"),
        "12345678900",
        "tpp-simulado"
    );
    consentStore.save(consent, Duration.ofMinutes(30)).block();
  }

  @Test
  void given_sameToken_when_replayAttack_then_returns401() throws JOSEException {
    String token = TestUtils.generateToken(UUID.randomUUID().toString());

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + token)
        .header("X-Cert-Thumbprint", CLIENT_THUMBPRINT)
        .exchange()
        .expectStatus().value(status ->
            assertThat(status)
                .isIn(200, 500, 502));

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + token)
        .header("X-Cert-Thumbprint", CLIENT_THUMBPRINT)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void given_distinctTokens_when_requests_then_bothPass() throws JOSEException {
    String token = TestUtils.generateToken(UUID.randomUUID().toString());
    String token2 = TestUtils.generateToken(UUID.randomUUID().toString());

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + token)
        .header("X-Cert-Thumbprint", CLIENT_THUMBPRINT)
        .exchange()
        .expectStatus().value(status ->
            assertThat(status)
                .isIn(200, 500, 502));

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + token2)
        .header("X-Cert-Thumbprint", CLIENT_THUMBPRINT)
        .exchange()
        .expectStatus().value(status ->
            assertThat(status).isIn(200, 500, 502));
  }
}
