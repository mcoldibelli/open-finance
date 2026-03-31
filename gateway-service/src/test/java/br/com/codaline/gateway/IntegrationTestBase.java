package br.com.codaline.gateway;

import br.com.codaline.gateway.config.CertificateGenerator;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {

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

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    registry.add("kafka.topics.audit", () -> "test-audit");
  }
}
