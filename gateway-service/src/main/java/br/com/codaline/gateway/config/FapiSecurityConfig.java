package br.com.codaline.gateway.config;

import br.com.codaline.gateway.filter.FapiMtlsValidationFilter;
import br.com.codaline.gateway.security.JwtVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@ConditionalOnResource(resources = "classpath:as-public.pem")
public class FapiSecurityConfig {

  @Bean
  public RSASSAVerifier rsassaVerifier(
      @Value("classpath:as-public.pem") Resource publicKeyResource)
      throws GeneralSecurityException, IOException {
    RSAPublicKey publicKey = loadPublicKey(publicKeyResource);
    return new RSASSAVerifier(publicKey);
  }

  @Bean
  public JwtVerifier jwtVerifier(RSASSAVerifier verifier,
      @Value("${jwt.issuer:https://as.localdev.codaline}") String expectedIssuer,
      @Value("${jwt.audience:https://gateway.localdev.codaline}") String expectedAudience) {
    return new JwtVerifier(verifier, expectedIssuer, expectedAudience);
  }

  @Bean
  public FapiMtlsValidationFilter fapiMtlsValidationFilter(
      @Value("${server.ssl.enabled:false}") boolean sslEnabled,
      JwtVerifier jwtVerifier) {
    return new FapiMtlsValidationFilter(sslEnabled, jwtVerifier);
  }

  private RSAPublicKey loadPublicKey(Resource resource)
      throws GeneralSecurityException, IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource.getInputStream())
    )) {
      String pem = reader.lines().collect(Collectors.joining("\n"));
      String base64 = pem
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s+", "");

      byte[] decoded = Base64.getDecoder().decode(base64);
      X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
      return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
  }
}
