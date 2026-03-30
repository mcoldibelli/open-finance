package br.com.codaline.gateway.config;

import br.com.codaline.gateway.filter.FapiMtlsValidationFilter;
import br.com.codaline.gateway.security.JwkSetProvider;
import br.com.codaline.gateway.security.JwtVerifier;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FapiSecurityConfig {

  @Bean
  public JwkSetProvider jwkSetProvider(
      @Value("${jwks.url}") String jwksUrl,
      @Value("${jwks.cache-ttl:5m}") Duration cacheTtl) {
    return new JwkSetProvider(jwksUrl, cacheTtl);
  }

  @Bean
  public JwtVerifier jwtVerifier(JwkSetProvider jwkSetProvider,
      @Value("${jwt.issuer:https://as.localdev.codaline}") String expectedIssuer,
      @Value("${jwt.audience:https://gateway.localdev.codaline}") String expectedAudience) {
    return new JwtVerifier(jwkSetProvider, expectedIssuer, expectedAudience);
  }

  @Bean
  public FapiMtlsValidationFilter fapiMtlsValidationFilter(
      @Value("${server.ssl.enabled:false}") boolean sslEnabled,
      @Value("${gateway.trust-proxy-headers:false}") boolean trustProxyHeaders,
      JwtVerifier jwtVerifier) {
    return new FapiMtlsValidationFilter(sslEnabled, trustProxyHeaders, jwtVerifier);
  }
}
