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
package org.apache.camel.quarkus.core;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@QuarkusTest
class ReactiveTemplatesTest {

    @Test
    void testReactiveProducerTemplateRequestBody() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("hello")
                .when()
                .post("/reactive-templates/producer/request-body")
                .then()
                .statusCode(200)
                .body(is("HELLO"));
    }

    @Test
    void testReactiveProducerTemplateRequestBodyAndHeader() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("hello")
                .when()
                .post("/reactive-templates/producer/request-body-and-header")
                .then()
                .statusCode(200)
                .body(is("hello - headerValue"));
    }

    @Test
    void testReactiveProducerTemplateRequestBodyAndHeaders() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("hello")
                .when()
                .post("/reactive-templates/producer/request-body-and-headers")
                .then()
                .statusCode(200)
                .body(is("hello - value1 - value2"));
    }

    @Test
    void testReactiveProducerTemplateSendBody() {
        // Send a message to the queue
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("test-message")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200)
                .body(is("sent"));

        // Verify it's in the queue by receiving
        RestAssured.when()
                .get("/reactive-templates/consumer/receive/5000")
                .then()
                .statusCode(200)
                .body(is("test-message"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveNoWait() {
        // Queue should be empty
        RestAssured.when()
                .get("/reactive-templates/consumer/receive-no-wait")
                .then()
                .statusCode(200)
                .body(is("null"));

        // Send a message
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("quick-message")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        // Should receive immediately
        RestAssured.when()
                .get("/reactive-templates/consumer/receive-no-wait")
                .then()
                .statusCode(200)
                .body(is("quick-message"));
    }

    @Test
    void testReactiveConsumerTemplateTimeout() {
        // Queue is empty, should timeout and return null
        RestAssured.when()
                .get("/reactive-templates/consumer/receive/1000")
                .then()
                .statusCode(200)
                .body(is("null"));
    }

    @Test
    void testErrorHandling() {
        RestAssured.when()
                .get("/reactive-templates/error/producer")
                .then()
                .statusCode(200)
                .body(is("error-handled"));
    }

    @Test
    void testChaining() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("chained")
                .when()
                .post("/reactive-templates/chain")
                .then()
                .statusCode(200)
                .body(is("PREFIX:CHAINED"));
    }

    // Endpoint-based variants
    @Test
    void testReactiveProducerTemplateRequestBodyEndpoint() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("hello")
                .when()
                .post("/reactive-templates/producer/request-body-endpoint")
                .then()
                .statusCode(200)
                .body(is("HELLO"));
    }

    @Test
    void testReactiveProducerTemplateSendBodyEndpoint() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("endpoint-message")
                .when()
                .post("/reactive-templates/producer/send-body-endpoint")
                .then()
                .statusCode(200)
                .body(is("sent"));

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-endpoint/5000")
                .then()
                .statusCode(200)
                .body(is("endpoint-message"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveNoWaitEndpoint() {
        RestAssured.when()
                .get("/reactive-templates/consumer/receive-no-wait-endpoint")
                .then()
                .statusCode(200)
                .body(is("null"));

        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("endpoint-quick")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-no-wait-endpoint")
                .then()
                .statusCode(200)
                .body(is("endpoint-quick"));
    }

    // Untyped variants
    @Test
    void testReactiveProducerTemplateRequestBodyUntyped() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("untyped")
                .when()
                .post("/reactive-templates/producer/request-body-untyped")
                .then()
                .statusCode(200)
                .body(is("UNTYPED"));
    }

    @Test
    void testReactiveProducerTemplateRequestBodyAndHeaderUntyped() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("untyped")
                .when()
                .post("/reactive-templates/producer/request-body-and-header-untyped")
                .then()
                .statusCode(200)
                .body(is("untyped - headerValue"));
    }

    @Test
    void testReactiveProducerTemplateRequestBodyAndHeadersUntyped() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("untyped")
                .when()
                .post("/reactive-templates/producer/request-body-and-headers-untyped")
                .then()
                .statusCode(200)
                .body(is("untyped - value1 - value2"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveBodyUntyped() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("untyped-message")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-body-untyped/5000")
                .then()
                .statusCode(200)
                .body(is("untyped-message"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveBodyNoWaitUntyped() {
        RestAssured.when()
                .get("/reactive-templates/consumer/receive-body-no-wait-untyped")
                .then()
                .statusCode(200)
                .body(is("null"));

        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("untyped-quick")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-body-no-wait-untyped")
                .then()
                .statusCode(200)
                .body(is("untyped-quick"));
    }

    // Exchange-based methods
    @Test
    void testReactiveProducerTemplateSendExchange() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("exchange")
                .when()
                .post("/reactive-templates/producer/send-exchange")
                .then()
                .statusCode(200)
                .body(is("EXCHANGE"));
    }

    @Test
    void testReactiveProducerTemplateSendProcessor() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("processor")
                .when()
                .post("/reactive-templates/producer/send-processor")
                .then()
                .statusCode(200)
                .body(is("PROCESSOR"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveExchange() {
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("exchange-message")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-exchange/5000")
                .then()
                .statusCode(200)
                .body(is("exchange-message"));
    }

    @Test
    void testReactiveConsumerTemplateReceiveExchangeNoWait() {
        RestAssured.when()
                .get("/reactive-templates/consumer/receive-exchange-no-wait")
                .then()
                .statusCode(200)
                .body(is("null"));

        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body("exchange-quick")
                .when()
                .post("/reactive-templates/producer/send-body")
                .then()
                .statusCode(200);

        RestAssured.when()
                .get("/reactive-templates/consumer/receive-exchange-no-wait")
                .then()
                .statusCode(200)
                .body(is("exchange-quick"));
    }
}
