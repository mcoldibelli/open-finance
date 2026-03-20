package br.com.codaline.gateway.filter;

import br.com.codaline.gateway.security.CertificateThumbprint;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.cert.X509Certificate;
import java.util.Map;
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

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

    SslInfo sslInfo = exchange.getRequest().getSslInfo();
    if (sslInfo == null || sslInfo.getPeerCertificates() == null
        || sslInfo.getPeerCertificates().length == 0) {
      return reject(exchange, "mTLS certificate required");
    }

    X509Certificate clientCert = sslInfo.getPeerCertificates()[0];
    String presentedThumbprint = CertificateThumbprint.computeS256(clientCert);

    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return reject(exchange, "Bearer token required");
    }

    String token = authHeader.substring(7);

    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWTClaimsSet claims = jwt.getJWTClaimsSet();

      // Verify for cnf.x5t#S256 - certificate-bound token (RFC 8705)
      Map<String, Object> cnf = (Map<String, Object>) claims.getClaim("cnf");
      if (cnf == null || !cnf.containsKey("x5t#S256")) {
        return reject(exchange, "Token missing cnf.x5t#S256 claim");
      }

      String tokenThumbprint = (String) cnf.get("x5t#S256");
      if (!presentedThumbprint.equals(tokenThumbprint)) {
        return reject(exchange, "Certificate thumbprint mismatch");
      }

      // Propagate downstream claims via internal headers
      String consentId = claims.getStringClaim("consent_id");
      String cpf = claims.getStringClaim("cpf");
      String clientId = claims.getStringClaim("client_id");

      ServerWebExchange mutated = exchange.mutate()
          .request(r -> r
              .header("X-Consent-ID", consentId != null ? consentId : "")
              .header("X-Caller-CPF", cpf != null ? cpf : "")
              .header("X-Client-ID", clientId != null ? clientId : "")
              .header("X-FAPI-Interaction-ID", claims.getJWTID() != null ? claims.getJWTID() : "")
              .header("X-Cert-Thumbprint", presentedThumbprint)
          ).build();

      return chain.filter(mutated);

    } catch (Exception e) {
      return reject(exchange, "Invalid token: " + e.getMessage());
    }
  }

  private Mono<Void> reject(ServerWebExchange exchange, String reason) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
