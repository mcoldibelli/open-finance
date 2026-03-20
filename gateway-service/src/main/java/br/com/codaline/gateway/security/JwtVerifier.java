package br.com.codaline.gateway.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class JwtVerifier {

  @Value("classpath:as-public.pem")
  private Resource publicKeyResource;

  @Value("${jwt.issuer:https://as.localdev.codaline}")
  private String expectedIssuer;

  @Value("${jwt.audience:https://gateway.localdev.codaline}")
  private String expectedAudience;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private RSASSAVerifier verifier;

  @PostConstruct
  public void init() throws GeneralSecurityException, IOException {
    RSAPublicKey publicKey = loadPublicKey();
    this.verifier = new RSASSAVerifier(publicKey);
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
        throw new SecurityException("Invalid issuer: " + claims.getIssuer());
      }

      if (!claims.getAudience().contains(expectedAudience)) {
        throw new SecurityException("Invalid audience: " + claims.getAudience());
      }

      return claims;
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private RSAPublicKey loadPublicKey() throws GeneralSecurityException, IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(publicKeyResource.getInputStream())
    )) {
      String pem = reader.lines().collect(Collectors.joining("\n"));
      String base64 = pem
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll(WHITESPACE.pattern(), "");

      byte[] decoded = Base64.getDecoder().decode(base64);
      X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
      return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
  }

}
