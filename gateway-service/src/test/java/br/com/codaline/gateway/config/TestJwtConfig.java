package br.com.codaline.gateway.config;

import br.com.codaline.gateway.IntegrationTestBase;
import br.com.codaline.gateway.security.JwtVerifier;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestJwtConfig {

  @Bean
  public JwtVerifier jwtVerifier() {
    JwtVerifier verifier = new JwtVerifier();
    verifier.setPublicKey(
        (RSAPublicKey) IntegrationTestBase.AS_KEY_PAIR.getPublic());
    return verifier;
  }
}
