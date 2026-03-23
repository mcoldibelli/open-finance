package br.com.codaline.gateway.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class JwtVerifier {

  private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

  private final RSASSAVerifier verifier;
  private final String expectedIssuer;
  private final String expectedAudience;

  public JwtVerifier(RSASSAVerifier verifier,
      @Value("${jwt.issuer:https://as.localdev.codaline}") String expectedIssuer,
      @Value("${jwt.audience:https://gateway.localdev.codaline}") String expectedAudience) {
    this.verifier = verifier;
    this.expectedIssuer = expectedIssuer;
    this.expectedAudience = expectedAudience;
  }

  public Mono<JWTClaimsSet> verify(String token) {
    return Mono.fromCallable(() -> {
      SignedJWT jwt = SignedJWT.parse(token);

      if (!jwt.verify(verifier)) {
        throw new SecurityException("Invalid JWT signature");
      }

      JWTClaimsSet claims = jwt.getJWTClaimsSet();

      Date now = new Date();
      if (claims.getExpirationTime() == null || claims.getExpirationTime().before(now)) {
        throw new SecurityException("JWT is expired");
      }

      if (!expectedIssuer.equals(claims.getIssuer())) {
        log.warn("JWT issuer mismatch: expected={}, actual={}", expectedIssuer, claims.getIssuer());
        throw new SecurityException("Invalid token issuer");
      }

      if (!claims.getAudience().contains(expectedAudience)) {
        log.warn("JWT audience mismatch: expected={}, actual={}", expectedAudience, claims.getAudience());
        throw new SecurityException("Invalid token audience");
      }

      return claims;
    }).subscribeOn(Schedulers.boundedElastic());
  }

}
