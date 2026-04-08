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
package org.apache.camel.quarkus.component.langchain4j.agent.it;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Integration test for AiService bean usage with langchain4j-agent endpoint.
 *
 * Tests the core functionality of User Story 1 (P1):
 * - AiService bean can be referenced in endpoint URI
 * - Messages are processed by the AiService
 * - Responses are returned correctly
 */
@QuarkusTest
class AiServiceBeanTest {

    /**
     * Test that an AiService bean can be referenced by name in a langchain4j-agent endpoint.
     *
     * Acceptance Criteria:
     * - AiService bean named "testAssistant" can be referenced via agent=#testAssistant$Agent
     * - Message sent through the route is processed by the AiService
     * - AiService returns a response
     */
    @Test
    void testAiServiceBeanReference() {
        String question = "What is 2+2?";

        RestAssured.given()
                .contentType("text/plain")
                .body(question)
                .when()
                .post("/langchain4j-agent-aiservice/test-aiservice")
                .then()
                .statusCode(200)
                .body(containsString("TestAssistant has been resolved and processed the request"));
    }

    /**
     * Test that multiple requests to the same AiService work correctly.
     */
    @Test
    void testMultipleRequests() {
        String expectedResponse = "TestAssistant has been resolved and processed the request";
        String question1 = "First question";
        String question2 = "Second question";

        // First request
        RestAssured.given()
                .contentType("text/plain")
                .body(question1)
                .when()
                .post("/langchain4j-agent-aiservice/test-aiservice")
                .then()
                .statusCode(200)
                .body(containsString(expectedResponse));

        // Second request
        RestAssured.given()
                .contentType("text/plain")
                .body(question2)
                .when()
                .post("/langchain4j-agent-aiservice/test-aiservice")
                .then()
                .statusCode(200)
                .body(containsString(expectedResponse));
    }
}
