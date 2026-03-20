package br.com.codaline.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component("consentKeyResolver")
public class ConsentKeyResolver implements KeyResolver {

  private static final String CONSENT_HEADER = "X-Consent-ID";
  private static final String FALLBACK_KEY = "anonymous";

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    String consentId = exchange.getRequest()
        .getHeaders()
        .getFirst(CONSENT_HEADER);

    if (consentId == null || consentId.isBlank()) {
      return Mono.just(FALLBACK_KEY);
    }

    return Mono.just(consentId);
  }
}
