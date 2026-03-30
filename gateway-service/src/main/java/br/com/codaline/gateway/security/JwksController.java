package br.com.codaline.gateway.security;

import com.nimbusds.jose.jwk.JWKSet;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class JwksController {

  private final JwkSetProvider jwkSetProvider;

  public JwksController(JwkSetProvider jwkSetProvider) {
    this.jwkSetProvider = jwkSetProvider;
  }

  @GetMapping("/.well-known/jwks.json")
  public Mono<ResponseEntity<Map<String, Object>>> jwks() {
    JWKSet cached = jwkSetProvider.getJwkSet();
    if (cached != null) {
      return Mono.just(ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
          .body(cached.toJSONObject()));
    }
    return jwkSetProvider.refreshKeys()
        .map(set -> ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
            .body(set.toJSONObject()));
  }
}
