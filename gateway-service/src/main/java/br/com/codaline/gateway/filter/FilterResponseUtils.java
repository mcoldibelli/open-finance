package br.com.codaline.gateway.filter;

import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class FilterResponseUtils {

  private FilterResponseUtils() {
  }

  public static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status,
      String reason, Logger log) {
    return Mono.defer(() -> {
      log.warn("Request rejected: {}", reason);
      exchange.getResponse().setStatusCode(status);
      exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
      return exchange.getResponse().setComplete();
    });
  }
}
