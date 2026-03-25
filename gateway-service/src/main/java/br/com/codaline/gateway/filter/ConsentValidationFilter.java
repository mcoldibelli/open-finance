package br.com.codaline.gateway.filter;

import br.com.codaline.gateway.consent.ConsentStatus;
import br.com.codaline.gateway.consent.ConsentStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ConsentValidationFilter implements GatewayFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(ConsentValidationFilter.class);
  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private final ConsentStore consentStore;

  public ConsentValidationFilter(ConsentStore consentStore) {
    this.consentStore = consentStore;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String consentId = exchange.getRequest().getHeaders().getFirst("X-Consent-ID");

    if (consentId == null || consentId.isBlank()) {
      return reject(exchange, HttpStatus.UNAUTHORIZED, "Consent ID missing");
    }

    String path = exchange.getRequest().getPath().value();
    String requiredPermission = resolvePermission(path);

    return consentStore.findByConsentId(consentId)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(optConsent -> {
          if (optConsent.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Consent not found");
          }

          var consent = optConsent.get();

          if (consent.status() != ConsentStatus.AUTHORISED) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Consent is " + consent.status());
          }

          if (!consent.permissions().contains(requiredPermission)) {
            return reject(exchange, HttpStatus.FORBIDDEN,
                "Permission " + requiredPermission + " not granted");
          }

          return chain.filter(exchange);
        });
  }

  private String resolvePermission(String path) {
    if (PATH_MATCHER.match("/**/transactions/**", path)
        || PATH_MATCHER.match("/**/transactions", path)) {
      return "ACCOUNTS_TRANSACTIONS_READ";
    }
    if (PATH_MATCHER.match("/**/balances/**", path)
        || PATH_MATCHER.match("/**/balances", path)) {
      return "ACCOUNTS_BALANCES_READ";
    }
    return "ACCOUNTS_READ";
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String reason) {
    return FilterResponseUtils.reject(exchange, status, reason, log);
  }

  @Override
  public int getOrder() {
    return -90;
  }
}
