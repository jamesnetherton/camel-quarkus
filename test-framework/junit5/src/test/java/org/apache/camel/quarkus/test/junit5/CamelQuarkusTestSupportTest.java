package org.apache.camel.quarkus.test.junit5;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.core.FastCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CamelQuarkusTestSupportTest extends CamelQuarkusTestSupport {

    @Inject
    CamelContext context;

    public CamelQuarkusTestSupportTest() {
        new Throwable().printStackTrace();
    }

    @Test
    public void camelContextIsFastCamelContext() {
        Assertions.assertInstanceOf(FastCamelContext.class, context);
    }

    @Test
    public void invokeSimpleRoute() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello World");

        template.requestBody("direct:start", null, String.class);

        mockEndpoint.assertIsSatisfied(5000);
    }

    @ApplicationScoped
    static final class TestRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start")
                    .setBody().constant("Hello World")
                    .to("mock:result");
        }
    }
}
