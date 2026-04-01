package br.com.codaline.gateway;

import br.com.codaline.gateway.audit.AuditEventPublisher;
import br.com.codaline.gateway.config.CertificateGenerator;
import br.com.codaline.gateway.filter.FapiMtlsValidationFilter;
import br.com.codaline.gateway.security.JwkSetProvider;
import br.com.codaline.gateway.security.JwtVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestBase.TestSecurityConfig.class)
@Testcontainers
public abstract class IntegrationTestBase {

  public static final String TEST_KID = "test-key-1";
  public static final KeyPair AS_KEY_PAIR;
  public static final KeyPair CLIENT_KEY_PAIR;
  public static final X509Certificate CLIENT_CERTIFICATE;
  public static final String CLIENT_THUMBPRINT;
  private static final String REDIS_PASSWORD = "test-password";

  @Container
  static final GenericContainer<?> redis = new GenericContainer<>(
      DockerImageName.parse("redis:7.2-alpine"))
      .withExposedPorts(6379)
      .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

  static {
    try {
      AS_KEY_PAIR = CertificateGenerator.generateRsaKeyPair();
      CLIENT_KEY_PAIR = CertificateGenerator.generateRsaKeyPair();
      CLIENT_CERTIFICATE = CertificateGenerator.generateCertificate(
          CLIENT_KEY_PAIR, "tpp-test");
      CLIENT_THUMBPRINT = CertificateGenerator.computeThumbprintS256(CLIENT_CERTIFICATE);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @MockitoBean
  AuditEventPublisher auditEventPublisher;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    registry.add("kafka.topics.audit", () -> "test-audit");
    registry.add("fapi.security.enabled", () -> "false");
  }

  @TestConfiguration
  static class TestSecurityConfig {

    @Bean
    public JwkSetProvider jwkSetProvider() {
      RSAKey rsaKey = new RSAKey.Builder(
          (RSAPublicKey) AS_KEY_PAIR.getPublic())
          .keyID(TEST_KID)
          .build();
      return JwkSetProvider.withPreloadedKeys(new JWKSet(rsaKey));
    }

    @Bean
    public JwtVerifier jwtVerifier(JwkSetProvider jwkSetProvider) {
      return new JwtVerifier(jwkSetProvider,
          "https://as.localdev.codaline",
          "https://gateway.localdev.codaline");
    }

    @Bean
    public FapiMtlsValidationFilter fapiMtlsValidationFilter(
        JwtVerifier jwtVerifier, AuditEventPublisher auditPublisher) {
      return new FapiMtlsValidationFilter(false, true, jwtVerifier, auditPublisher);
    }
  }
}
