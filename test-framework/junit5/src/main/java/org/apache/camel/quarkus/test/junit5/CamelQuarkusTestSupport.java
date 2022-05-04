package org.apache.camel.quarkus.test.junit5;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.create;

public class CamelQuarkusTestSupport implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Logger LOG = LoggerFactory.getLogger(CamelQuarkusTestSupport.class);
    private static final ExtensionContext.Namespace NAMESPACE = create(CamelQuarkusTestSupport.class);
    private static final String WATCH = "watch";

    @RegisterExtension
    private final CamelQuarkusTestSupport extension = this;

    @Inject
    protected CamelContext context;

    @Inject
    protected ProducerTemplate template;

    @Inject
    protected ConsumerTemplate consumerTemplate;

    @Inject
    protected FluentProducerTemplate fluentTemplate;

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getStore(NAMESPACE).put(WATCH, new StopWatch());
        if (LOG.isInfoEnabled()) {
            final Class<?> requiredTestClass = context.getRequiredTestClass();
            final String currentTestName = context.getDisplayName();
            LOG.info("********************************************************************************");
            LOG.info("Testing: {} ({})", currentTestName, requiredTestClass.getName());
            LOG.info("********************************************************************************");
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        final long time = context.getStore(NAMESPACE).remove(WATCH, StopWatch.class).taken();
        final String currentTestName = context.getDisplayName();
        if (LOG.isInfoEnabled()) {
            final Class<?> requiredTestClass = context.getRequiredTestClass();
            LOG.info("********************************************************************************");
            LOG.info("Testing done: {} ({})", currentTestName, requiredTestClass.getName());
            LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time), time);
            LOG.info("********************************************************************************");
        }
    }

    @BeforeAll
    public static void beforeAll(ExtensionContext context) {
        System.out.println("===============> " + context);
        context.toString();
    }

    public void afterAll(ExtensionContext context) {
        context.toString();
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an exception is thrown
     *
     * @param  uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return     the endpoint
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return TestSupport.resolveMandatoryEndpoint(context, uri, endpointType);
    }

    /**
     * Resolves the mandatory Mock endpoint using a URI of the form <code>mock:someName</code>
     *
     * @param  uri the URI which typically starts with "mock:" and has some name
     * @return     the mandatory mock endpoint or an exception is thrown if it could not be resolved
     */
    protected MockEndpoint getMockEndpoint(String uri) {
        return getMockEndpoint(uri, true);
    }

    /**
     * Resolves the {@link MockEndpoint} using a URI of the form <code>mock:someName</code>, optionally creating it if
     * it does not exist. This implementation will lookup existing mock endpoints and match on the mock queue name, eg
     * mock:foo and mock:foo?retainFirst=5 would match as the queue name is foo.
     *
     * @param  uri                     the URI which typically starts with "mock:" and has some name
     * @param  create                  whether or not to allow the endpoint to be created if it doesn't exist
     * @return                         the mock endpoint or an {@link NoSuchEndpointException} is thrown if it could not
     *                                 be resolved
     * @throws NoSuchEndpointException is the mock endpoint does not exists
     */
    protected MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        // look for existing mock endpoints that have the same queue name, and
        // to
        // do that we need to normalize uri and strip out query parameters and
        // whatnot
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        // strip query
        int idx = n.indexOf('?');
        if (idx != -1) {
            n = n.substring(0, idx);
        }
        final String target = n;

        // lookup endpoints in registry and try to find it
        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream()
                .filter(e -> e instanceof MockEndpoint).filter(e -> {
                    String t = e.getEndpointUri();
                    // strip query
                    int idx2 = t.indexOf('?');
                    if (idx2 != -1) {
                        t = t.substring(0, idx2);
                    }
                    return t.equals(target);
                }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return resolveMandatoryEndpoint(uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }

    /**
     * Sends a message to the given endpoint URI with the body value
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     */
    protected void sendBody(String endpointUri, final Object body) {
        template.send(endpointUri, exchange -> {
            Message in = exchange.getIn();
            in.setBody(body);
        });
    }

    /**
     * Sends a message to the given endpoint URI with the body value and specified headers
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     * @param headers     any headers to set on the message
     */
    protected void sendBody(String endpointUri, final Object body, final Map<String, Object> headers) {
        template.send(endpointUri, exchange -> {
            Message in = exchange.getIn();
            in.setBody(body);
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                in.setHeader(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Sends messages to the given endpoint for each of the specified bodies
     *
     * @param endpointUri the endpoint URI to send to
     * @param bodies      the bodies to send, one per message
     */
    protected void sendBodies(String endpointUri, Object... bodies) {
        for (Object body : bodies) {
            sendBody(endpointUri, body);
        }
    }

    /**
     * Creates an exchange with the given body
     */
    protected Exchange createExchangeWithBody(Object body) {
        return TestSupport.createExchangeWithBody(context, body);
    }

    /**
     * Asserts that all the expectations of the Mock endpoints are valid
     */
    protected void assertMockEndpointsSatisfied() throws InterruptedException {
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Asserts that all the expectations of the Mock endpoints are valid
     */
    protected void assertMockEndpointsSatisfied(long timeout, TimeUnit unit) throws InterruptedException {
        MockEndpoint.assertIsSatisfied(context, timeout, unit);
    }

    /**
     * Reset all Mock endpoints.
     */
    protected void resetMocks() {
        MockEndpoint.resetMocks(context);
    }

    private ExtensionContext.Store getContextStore(ExtensionContext context) {
        ExtensionContext sourceContext = context;
        if (context.getTestInstanceLifecycle().stream()
                .anyMatch(lifecycle -> lifecycle.equals(TestInstance.Lifecycle.PER_CLASS))) {
            sourceContext = context.getParent().orElseThrow();
        }
        return sourceContext.getStore(NAMESPACE);
    }
}
