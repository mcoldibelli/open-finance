package br.com.codaline.gateway.config;

import br.com.codaline.gateway.IntegrationTestBase;
import br.com.codaline.gateway.filter.FapiMtlsValidationFilter;
import br.com.codaline.gateway.security.JwtVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestJwtConfig {

  @Bean
  public RSASSAVerifier rsassaVerifier() {
    return new RSASSAVerifier(
        (RSAPublicKey) IntegrationTestBase.AS_KEY_PAIR.getPublic());
  }

  @Bean
  public JwtVerifier jwtVerifier(RSASSAVerifier verifier) {
    return new JwtVerifier(verifier,
        "https://as.localdev.codaline",
        "https://gateway.localdev.codaline");
  }

  @Bean
  public FapiMtlsValidationFilter fapiMtlsValidationFilter(JwtVerifier jwtVerifier) {
    return new FapiMtlsValidationFilter(false, jwtVerifier);
  }
}
