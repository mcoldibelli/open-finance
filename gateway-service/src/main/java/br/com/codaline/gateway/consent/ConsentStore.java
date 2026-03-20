package br.com.codaline.gateway.consent;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ConsentStore {

  private static final String KEY_PREFIX = "consent";
  private final ReactiveRedisTemplate<String, String> redis;

  public ConsentStore(ReactiveRedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  public Mono<ConsentData> findByConsentId(String consentId) {
    String key = KEY_PREFIX + consentId;

    return redis.opsForHash().entries(key)
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .filter(fields -> !fields.isEmpty())
        .map(this::toConsentData);
  }

  public Mono<Void> save(ConsentData consent, Duration ttl) {
    String key = KEY_PREFIX + consent.getConsentId();

    Map<String, String> fields = Map.of(
        "status", consent.getStatus().name(),
        "permissions", String.join(",", consent.getPermissions()),
        "cpf", consent.getLinkedCpf(),
        "client_id", consent.getClientId()
    );

    return redis.opsForHash().putAll(key, fields)
        .then(redis.expire(key, ttl))
        .then();
  }

  private ConsentData toConsentData(Map<String, String> fields) {
    return new ConsentData(
        null,
        ConsentStatus.valueOf(fields.get("status")),
        new HashSet<>(Arrays.asList(fields.get("permissions").split(","))),
        fields.get("cpf"),
        fields.get("client_id")
    );
  }
}
