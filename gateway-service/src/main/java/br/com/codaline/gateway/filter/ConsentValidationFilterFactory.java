package br.com.codaline.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsentValidationFilterFactory
    extends AbstractGatewayFilterFactory<Object> {

  private final ConsentValidationFilter filter;

  public ConsentValidationFilterFactory(ConsentValidationFilter filter) {
    super(Object.class);
    this.filter = filter;
  }

  @Override
  public GatewayFilter apply(Object config) {
    return filter;
  }

  @Override
  public String name() {
    return "ConsentValidation";
  }
}