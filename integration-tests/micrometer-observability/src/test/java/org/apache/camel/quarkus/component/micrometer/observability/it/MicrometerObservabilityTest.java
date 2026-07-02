/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.micrometer.observability.it;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.SpanKind;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.quarkus.component.micrometer.observability.it.MicrometerObservabilityTestHelper.getSpans;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MicrometerObservabilityTest {

    @AfterEach
    public void afterEach() {
        RestAssured.post("/micrometer-observability/exporter/spans/reset")
                .then()
                .statusCode(204);
    }

    @Test
    public void testTraceRoute() {
        int nTraces = 5;
        int spanPerTrace = 4;
        for (int i = 0; i < nTraces; i++) {
            RestAssured.get("/micrometer-observability/test/trace/")
                    .then()
                    .statusCode(200);
        }

        await().atMost(30, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS)
                .until(() -> getSpans().size() == (nTraces * spanPerTrace));
        List<Map<String, String>> spans = getSpans();
        assertEquals(nTraces * spanPerTrace, spans.size());

        for (int i = spans.size() - 1; i >= 0; i--) {
            Map<String, String> span = spans.get(i);

            if (i % 4 == 2) {
                assertEquals("camel-platform-http", span.get("component"));
                assertEquals("200", span.get("http.status_code"));
                assertEquals("GET", span.get("http.method"));
                assertEquals("platform-http:///micrometer-observability/test/trace?httpMethodRestrict=GET",
                        span.get("camel.uri"));
                assertTrue(span.get("http.url").endsWith("/micrometer-observability/test/trace/"));
                assertEquals(SpanKind.INTERNAL.name(), span.get("kind"));
                assertEquals(spans.get(i + 1).get("spanId"), span.get("parentId"));
            } else if (i % 4 == 3) {
                assertEquals("200", span.get("http.response.status_code"));
                assertEquals("GET", span.get("http.request.method"));
                assertEquals("/micrometer-observability/test/trace/", span.get("url.path"));
                assertEquals(SpanKind.SERVER.name(), span.get("kind"));
                assertEquals("0000000000000000", span.get("parentId"));
            }
        }
    }

    @Test
    public void testTracedCamelRouteInvokedFromJaxRsService() {
        int spanPerTrace = 4;
        RestAssured.get("/micrometer-observability/trace")
                .then()
                .statusCode(200)
                .body(equalTo("Traced direct:start"));

        await().atMost(30, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS)
                .until(() -> getSpans().size() == spanPerTrace);
        List<Map<String, String>> spans = getSpans();
        assertEquals(spanPerTrace, spans.size());
        assertEquals(spans.get(0).get("parentId"), spans.get(1).get("spanId"));
        assertEquals(SpanKind.INTERNAL.name(), spans.get(1).get("kind"));
        assertEquals(SpanKind.INTERNAL.name(), spans.get(2).get("kind"));
        assertEquals(SpanKind.SERVER.name(), spans.get(3).get("kind"));
    }

    @Test
    public void testTracedBean() {
        int spanPerTrace = 5;
        String name = "Camel Quarkus Micrometer Observability";
        RestAssured.get("/micrometer-observability/greet/" + name)
                .then()
                .statusCode(200)
                .body(equalTo("Hello " + name));

        await().atMost(30, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS)
                .until(() -> getSpans().size() == spanPerTrace);
        List<Map<String, String>> spans = getSpans();
        assertEquals(spanPerTrace, spans.size());

        assertEquals(spans.get(2).get("spanId"), spans.get(0).get("parentId"));
        assertEquals(spans.get(2).get("spanId"), spans.get(1).get("parentId"));
        assertEquals(spans.get(3).get("spanId"), spans.get(2).get("parentId"));
        assertEquals(SpanKind.INTERNAL.name(), spans.get(3).get("kind"));
        assertEquals(SpanKind.SERVER.name(), spans.get(4).get("kind"));
    }

    @Test
    void traceHeaderInclusion() {
        RestAssured.get("/micrometer-observability/trace/headers")
                .then()
                .statusCode(204)
                .header("spanId", not(emptyOrNullString()))
                .header("traceId", not(emptyOrNullString()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "http", "vertx-http" })
    void testHttpInvocation(String httpComponent) {
        int spanPerTrace = 9;
        RestAssured.given()
                .queryParam("httpComponent", httpComponent)
                .get("/greeting")
                .then()
                .statusCode(200)
                .body(equalTo("Hello From Camel Quarkus!"));

        await().atMost(30, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS)
                .until(() -> getSpans().size() == spanPerTrace);
        List<Map<String, String>> spans = getSpans();
        assertEquals(spanPerTrace, spans.size());

        Map<String, Map<String, String>> spanById = new java.util.HashMap<>();
        for (Map<String, String> span : spans) {
            spanById.put(span.get("spanId"), span);
        }

        // Find root span (no parent in this trace)
        Map<String, String> rootSpan = spans.stream()
                .filter(s -> "0000000000000000".equals(s.get("parentId")))
                .findFirst().orElseThrow();
        assertEquals(SpanKind.SERVER.name(), rootSpan.get("kind"));

        // Verify all spans share the same trace ID
        String traceId = rootSpan.get("traceId");
        assertTrue(spans.stream().allMatch(s -> traceId.equals(s.get("traceId"))));

        // Verify we have the expected number of SERVER spans (Vert.x /greeting + /greeting-provider)
        long serverSpanCount = spans.stream()
                .filter(s -> SpanKind.SERVER.name().equals(s.get("kind")))
                .count();
        assertEquals(2, serverSpanCount);
    }
}
