package br.com.codaline.gateway.filter;

import br.com.codaline.gateway.consent.ConsentStatus;
import br.com.codaline.gateway.consent.ConsentStore;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ConsentValidationFilter implements GatewayFilter, Ordered {

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
        .flatMap(consent -> {
          if (consent.getStatus() != ConsentStatus.AUTHORISED) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Consent is " + consent.getStatus());
          }

          if (!consent.getPermissions().contains(requiredPermission)) {
            return reject(exchange, HttpStatus.FORBIDDEN,
                "Permission " + requiredPermission + " not granted");
          }

          return chain.filter(exchange);
        })
        .switchIfEmpty(
            reject(exchange, HttpStatus.UNAUTHORIZED, "Consent not found"));
  }

  private String resolvePermission(String path) {
    if (path.contains("/balances")) {
      return "ACCOUNTS_BALANCES_READ";
    }
    if (path.contains("/transactions")) {
      return "ACCOUNTS_TRANSACTIONS_READ";
    }
    return "ACCOUNTS_READ";
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String reason) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return -90; // After FapiMtls (-100)
  }
}
