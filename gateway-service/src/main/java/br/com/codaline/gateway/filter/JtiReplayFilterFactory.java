package br.com.codaline.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class JtiReplayFilterFactory extends AbstractGatewayFilterFactory<Object> {

  private final JtiReplayFilter filter;

  public JtiReplayFilterFactory(JtiReplayFilter filter) {
    super(Object.class);
    this.filter = filter;
  }

  @Override
  public GatewayFilter apply(Object config) {
    return filter;
  }

  @Override
  public String name() {
    return "JtiReplay";
  }

}
