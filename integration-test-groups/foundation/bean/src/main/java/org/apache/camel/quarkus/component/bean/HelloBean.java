package org.apache.camel.quarkus.component.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Registry;

public class HelloBean {

    public String hello(CamelContext context, Exchange exchange, Registry registry) {
        System.out.println("=====> " + context);
        System.out.println("=====> " + exchange);
        System.out.println("=====> " + registry);
        return "Hello";
    }
}
