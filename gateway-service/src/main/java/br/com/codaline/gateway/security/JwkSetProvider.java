package br.com.codaline.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class JwkSetProvider {

  private final Duration cacheTtl;
  private final AtomicReference<JWKSet> jwkSet = new AtomicReference<>();
  private final WebClient webClient;
  private volatile Instant lastRefresh;

  private JwkSetProvider(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
    this.webClient = null;
  }

  public JwkSetProvider(String jwksUrl, Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
    this.webClient = WebClient.create(jwksUrl);
  }

  public static JwkSetProvider withPreloadedKeys(JWKSet keys) {
    var provider = new JwkSetProvider(Duration.ofHours(24));
    provider.jwkSet.set(keys);
    provider.lastRefresh = Instant.now();
    return provider;
  }

  Mono<JWKSet> refreshKeys() {
    return webClient.get()
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(body -> Mono.fromCallable(() -> JWKSet.parse(body)))
        .doOnNext(parsed -> {
          this.jwkSet.set(parsed);
          this.lastRefresh = Instant.now();
        });
  }

  public Mono<RSAPublicKey> getKey(String kid) {
    return getCachedOrRefresh()
        .flatMap(set -> extractKey(set, kid)
            .map(Mono::just)
            .orElseGet(() -> refreshKeys()
                .flatMap(refreshed -> extractKey(refreshed, kid)
                    .map(Mono::just)
                    .orElseGet(() -> Mono.error(
                        new SecurityException("Key not found: " + kid))))));
  }

  private Mono<JWKSet> getCachedOrRefresh() {
    JWKSet cached = jwkSet.get();
    if (cached != null && lastRefresh != null && Instant.now()
        .isBefore(lastRefresh.plus(cacheTtl))) {
      return Mono.just(cached);
    }
    return refreshKeys();
  }

  private Optional<RSAPublicKey> extractKey(JWKSet set, String kid) {
    return Optional.ofNullable(set.getKeyByKeyId(kid))
        .filter(RSAKey.class::isInstance)
        .map(RSAKey.class::cast)
        .map(rsaKey -> {
          try {
            return rsaKey.toRSAPublicKey();
          } catch (JOSEException e) {
            throw new SecurityException("Failed to extract RSA key", e);
          }
        });
  }

  public JWKSet getJwkSet() {
    return jwkSet.get();
  }
}
