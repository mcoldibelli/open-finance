package br.com.codaline.gateway;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import br.com.codaline.gateway.consent.ConsentData;
import br.com.codaline.gateway.consent.ConsentStatus;
import br.com.codaline.gateway.consent.ConsentStore;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "server.ssl.enabled=false",
    "spring.cloud.gateway.routes[0].id=accounts-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8081",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/open-banking/accounts/v2/**",
    "spring.cloud.gateway.routes[0].filters[0]=FapiMtlsValidation",
    "spring.cloud.gateway.routes[0].filters[1]=ConsentValidation",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=openfinance123"
})
class FapiConsentFilterTest {

  private static final String ISSUER = GenerateTestJwt.ISSUER;
  private static final String AUDIENCE = GenerateTestJwt.AUDIENCE;

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private ConsentStore consentStore;

  private String validToken;
  private String thumbprint;

  @BeforeEach
  void setup() throws Exception {
    thumbprint = GenerateTestJwt.loadClientThumbprint();
    RSAPrivateKey asPrivateKey = loadAsPrivateKey();

    ConsentData consent = new ConsentData(
        "consent-filter-test",
        ConsentStatus.AUTHORISED,
        Set.of("ACCOUNTS_READ", "ACCOUNTS_BALANCES_READ"),
        "12345678900",
        "tpp-simulado"
    );
    consentStore.save(consent, Duration.ofMinutes(5)).block();

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .claim("consent_id", "consent-filter-test")
        .claim("cpf", "12345678900")
        .claim("client_id", "tpp-simulado")
        .claim("cnf", Map.of("x5t#S256", thumbprint))
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims
    );
    jwt.sign(new RSASSASigner(asPrivateKey));
    validToken = jwt.serialize();
  }

  @Test
  void semToken_deveRetornar401() {
    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().exists("X-Rejection-Reason");
  }

  @Test
  void comTokenSemCnf_deveRetornar401() throws Exception {
    RSAPrivateKey asPrivateKey = loadAsPrivateKey();

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .claim("consent_id", "consent-filter-test")
        .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims
    );
    jwt.sign(new RSASSASigner(asPrivateKey));

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + jwt.serialize())
        .exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().valueEquals("X-Rejection-Reason",
            "Token missing cnf.x5t#S256 claim");
  }

  @Test
  void comTokenValido_consentimentoAutorizado_devePassar() {
    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + validToken)
        .header("X-Cert-Thumbprint", thumbprint)
        .exchange()
        .expectStatus().value(status ->
            org.assertj.core.api.Assertions.assertThat(status)
                .isIn(200, 502));
  }

  @Test
  void semPermissaoTransacoes_deveRetornar403() {
    webTestClient.get()
        .uri("/open-banking/accounts/v2/acc-001/transactions")
        .header("Authorization", "Bearer " + validToken)
        .header("X-Cert-Thumbprint", thumbprint)
        .exchange()
        .expectStatus().isForbidden()
        .expectHeader().value("X-Rejection-Reason", reason ->
            assertThat(reason).contains("Permission ACCOUNTS_TRANSACTIONS_READ not granted"));
  }

  @Test
  void consentimentoInexistente_deveRetornar401() throws Exception {
    RSAPrivateKey asPrivateKey = loadAsPrivateKey();

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .claim("consent_id", "consent-inexistente-999")
        .claim("cnf", Map.of("x5t#S256", thumbprint))
        .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims
    );
    jwt.sign(new RSASSASigner(asPrivateKey));

    webTestClient.get()
        .uri("/open-banking/accounts/v2")
        .header("Authorization", "Bearer " + jwt.serialize())
        .header("X-Cert-Thumbprint", thumbprint)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  private RSAPrivateKey loadAsPrivateKey() throws Exception {
    String pemPath = System.getProperty("user.home")
        + "/Documents/codaline/certs/open-finance/as-private.key";
    String pem = new String(Files.readAllBytes(Paths.get(pemPath)));
    String base64 = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s+", "");
    byte[] decoded = Base64.getDecoder().decode(base64);
    return (RSAPrivateKey) KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(decoded));
  }
}
