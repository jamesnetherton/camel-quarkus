package org.apache.camel.quarkus.component.smpp.it;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;

public class Routes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        Registry registry = getContext().getRegistry();
        registry.toString();
    }
}
