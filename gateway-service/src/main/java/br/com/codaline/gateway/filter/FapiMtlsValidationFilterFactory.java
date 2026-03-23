package br.com.codaline.gateway.filter;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class FapiMtlsValidationFilterFactory extends AbstractGatewayFilterFactory<Object> {

  private static final Logger log = LoggerFactory.getLogger(FapiMtlsValidationFilterFactory.class);
  private final GatewayFilter filter;

  public FapiMtlsValidationFilterFactory(Optional<FapiMtlsValidationFilter> fapiFilter) {
    super(Object.class);
    this.filter = fapiFilter
        .<GatewayFilter>map(f -> f)
        .orElseGet(() -> {
          log.warn("FAPI mTLS security DISABLED — as-public.pem not found on classpath");
          return (exchange, chain) -> chain.filter(exchange);
        });
  }

  @Override
  public GatewayFilter apply(Object config) {
    return filter;
  }

  @Override
  public String name() {
    return "FapiMtlsValidation";
  }
}
