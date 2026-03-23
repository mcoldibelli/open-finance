package br.com.codaline.gateway.filter;

import br.com.codaline.gateway.security.CertificateThumbprint;
import br.com.codaline.gateway.security.JwtVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class FapiMtlsValidationFilter implements GatewayFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(FapiMtlsValidationFilter.class);
  private final boolean sslEnabled;
  private final JwtVerifier jwtVerifier;

  public FapiMtlsValidationFilter(@Value("${server.ssl.enabled:false}") boolean sslEnabled,
      JwtVerifier jwtVerifier) {
    this.sslEnabled = sslEnabled;
    this.jwtVerifier = jwtVerifier;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Optional<String> thumbprint = extractThumbprint(exchange);
    if (thumbprint.isEmpty()) {
      return reject(exchange, "mTLS certificate required");
    }

    Optional<String> token = extractBearerToken(exchange);
    if (token.isEmpty()) {
      return reject(exchange, "Bearer token required");
    }

    String presentedThumbprint = thumbprint.get();
    return jwtVerifier.verify(token.get())
        .onErrorMap(e -> !(e instanceof SecurityException),
            e -> new SecurityException("Invalid token: " + e.getMessage()))
        .flatMap(claims -> {
          try {
            return processValidatedClaims(exchange, chain, claims, presentedThumbprint);
          } catch (Exception e) {
            return reject(exchange, "Token processing error");
          }
        })
        .onErrorResume(SecurityException.class, e -> reject(exchange, e.getMessage()));
  }

  private Optional<String> extractThumbprint(ServerWebExchange exchange) {
    if (sslEnabled) {
      SslInfo sslInfo = exchange.getRequest().getSslInfo();
      if (sslInfo == null) {
        return Optional.empty();
      }
      X509Certificate[] peerCertificates = sslInfo.getPeerCertificates();
      if (peerCertificates == null || peerCertificates.length == 0) {
        return Optional.empty();
      }
      return Optional.of(CertificateThumbprint.computeS256(peerCertificates[0]));
    }

    String headerThumbprint = exchange.getRequest().getHeaders().getFirst("X-Cert-Thumbprint");
    return Optional.ofNullable(headerThumbprint).filter(h -> !h.isBlank());
  }

  private Optional<String> extractBearerToken(ServerWebExchange exchange) {
    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return Optional.empty();
    }
    return Optional.of(authHeader.substring(7));
  }

  private Mono<Void> processValidatedClaims(ServerWebExchange exchange, GatewayFilterChain chain,
      JWTClaimsSet claims, String presentedThumbprint) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> cnf = (Map<String, Object>) claims.getClaim("cnf");
      if (cnf == null || !cnf.containsKey("x5t#S256")) {
        return reject(exchange, "Token missing cnf.x5t#S256 claim");
      }

      String tokenThumbprint = (String) cnf.get("x5t#S256");
      if (!presentedThumbprint.equals(tokenThumbprint)) {
        return reject(exchange, "Certificate thumbprint mismatch");
      }

      ServerWebExchange mutated = buildMutatedExchange(exchange, claims, presentedThumbprint);
      return chain.filter(mutated);
    } catch (ParseException e) {
      return reject(exchange, "Token processing error");
    }
  }

  private ServerWebExchange buildMutatedExchange(ServerWebExchange exchange, JWTClaimsSet claims,
      String presentedThumbprint) throws ParseException {
    String consentId = claims.getStringClaim("consent_id");
    String cpf = claims.getStringClaim("cpf");
    String clientId = claims.getStringClaim("client_id");
    String jti = claims.getJWTID();
    String tokenExp = String.valueOf(claims.getExpirationTime().getTime() / 1000);

    String fapiInteractionId = Optional.ofNullable(
            exchange.getRequest().getHeaders().getFirst("X-FAPI-Interaction-ID"))
        .filter(id -> !id.isBlank())
        .orElse(UUID.randomUUID().toString());

    return exchange.mutate()
        .request(r -> r.headers(h -> {
          h.set("X-Consent-ID", consentId != null ? consentId : "");
          h.set("X-Caller-CPF", cpf != null ? cpf : "");
          h.set("X-Client-ID", clientId != null ? clientId : "");
          h.set("X-FAPI-Interaction-ID", fapiInteractionId);
          h.set("X-Token-Jti", jti != null ? jti : "");
          h.set("X-Cert-Thumbprint", presentedThumbprint);
          h.set("X-Token-Exp", tokenExp);
        })).build();
  }

  private Mono<Void> reject(ServerWebExchange exchange, String reason) {
    return Mono.defer(() -> {
      log.warn("Request rejected: {}", reason);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
      return exchange.getResponse().setComplete();
    });
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
