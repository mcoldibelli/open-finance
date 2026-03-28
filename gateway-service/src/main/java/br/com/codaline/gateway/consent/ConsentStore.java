package br.com.codaline.gateway.consent;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ConsentStore {

  private static final Logger log = LoggerFactory.getLogger(ConsentStore.class);
  private static final String KEY_PREFIX = "consent:";

  private static final RedisScript<Boolean> SAVE_SCRIPT = RedisScript.of(
      "redis.call('HSET', KEYS[1], 'consent_id', ARGV[1], 'status', ARGV[2], " +
          "'permissions', ARGV[3], 'cpf', ARGV[4], 'client_id', ARGV[5])\n" +
          "redis.call('EXPIRE', KEYS[1], ARGV[6])\n" +
          "return true",
      Boolean.class
  );

  private final ReactiveRedisTemplate<String, String> redis;

  public ConsentStore(ReactiveRedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  public Mono<ConsentData> findByConsentId(String consentId) {
    String key = KEY_PREFIX + consentId;

    return redis.<String, String>opsForHash().entries(key)
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .filter(fields -> !fields.isEmpty())
        .flatMap(this::toConsentData);
  }

  public Mono<Void> save(ConsentData consent, Duration ttl) {
    String key = KEY_PREFIX + consent.consentId();

    return redis.execute(SAVE_SCRIPT,
            Collections.singletonList(key),
            List.of(
                consent.consentId(),
                consent.status().name(),
                String.join(",", consent.permissions()),
                consent.linkedCpf(),
                consent.clientId(),
                String.valueOf(ttl.getSeconds())
            ))
        .then();
  }

  Mono<ConsentData> toConsentData(Map<String, String> fields) {
    String statusRaw = fields.get("status");
    String permissionsRaw = fields.get("permissions");

    if (statusRaw == null || permissionsRaw == null) {
      log.warn("Corrupted consent data in Redis: status={}, permissions={}",
          statusRaw, permissionsRaw);
      return Mono.empty();
    }

    try {
      Set<String> permissions = Arrays.stream(permissionsRaw.split(","))
          .filter(s -> !s.isBlank())
          .collect(Collectors.toSet());

      if (permissions.isEmpty()) {
        log.warn("Empty permissions in Redis for consent: {}", fields.get("consent_id"));
        return Mono.empty();
      }

      return Mono.just(new ConsentData(
          fields.get("consent_id"),
          ConsentStatus.valueOf(statusRaw),
          permissions,
          fields.get("cpf"),
          fields.get("client_id")
      ));
    } catch (IllegalArgumentException e) {
      log.warn("Invalid consent status in Redis: {}", statusRaw);
      return Mono.empty();
    }
  }
}
