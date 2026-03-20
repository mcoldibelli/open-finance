package br.com.codaline.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class FapiMtlsValidationFilterFactory
    extends AbstractGatewayFilterFactory<Object> {

  private final FapiMtlsValidationFilter filter;

  public FapiMtlsValidationFilterFactory(FapiMtlsValidationFilter filter) {
    super(Object.class);
    this.filter = filter;
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