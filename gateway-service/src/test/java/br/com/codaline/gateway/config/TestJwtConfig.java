package br.com.codaline.gateway.config;

import br.com.codaline.gateway.IntegrationTestBase;
import br.com.codaline.gateway.filter.FapiMtlsValidationFilter;
import br.com.codaline.gateway.security.JwkSetProvider;
import br.com.codaline.gateway.security.JwtVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestJwtConfig {

  public static final String TEST_KID = "test-key-1";

  @Bean
  public JwkSetProvider jwkSetProvider() {
    RSAKey rsaKey = new RSAKey.Builder(
        (RSAPublicKey) IntegrationTestBase.AS_KEY_PAIR.getPublic())
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
  public FapiMtlsValidationFilter fapiMtlsValidationFilter(JwtVerifier jwtVerifier) {
    return new FapiMtlsValidationFilter(false, true, jwtVerifier);
  }
}
