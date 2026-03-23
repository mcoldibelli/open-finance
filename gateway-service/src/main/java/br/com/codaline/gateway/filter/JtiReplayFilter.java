package br.com.codaline.gateway.filter;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JtiReplayFilter implements GatewayFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(JtiReplayFilter.class);
  private final ReactiveRedisTemplate<String, String> redisTemplate;

  public JtiReplayFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String jti = exchange.getRequest().getHeaders().getFirst("X-Token-Jti");
    String expiresAt = exchange.getRequest().getHeaders().getFirst("X-Token-Exp");

    if (jti == null || jti.isBlank()) {
      return reject(exchange, "Missing JTI claim");
    }

    if (expiresAt == null || expiresAt.isBlank()) {
      return reject(exchange, "Missing X-Token-Exp");
    }

    long ttl;
    try {
      ttl = Long.parseLong(expiresAt) - Instant.now().getEpochSecond();
    } catch (NumberFormatException e) {
      return reject(exchange, "Invalid X-Token-Exp value");
    }

    if (ttl <= 0) {
      return reject(exchange, "Token expired");
    }

    return redisTemplate.opsForValue()
        .setIfAbsent("jti:" + jti, "1", Duration.ofSeconds(ttl))
        .flatMap(isNew -> {
          if (Boolean.TRUE.equals(isNew)) {
            return chain.filter(exchange);
          }
          return reject(exchange, "Token already used (replay detected)");
        });
  }

  private Mono<Void> reject(ServerWebExchange exchange, String reason) {
    return Mono.defer(() -> {
      log.warn("JTI rejected: {}", reason);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
      return exchange.getResponse().setComplete();
    });
  }

  @Override
  public int getOrder() {
    return -95;
  }
}
