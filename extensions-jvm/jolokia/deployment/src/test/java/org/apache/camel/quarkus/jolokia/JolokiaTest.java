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
package org.apache.camel.quarkus.jolokia;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JolokiaTest {
    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Routes.class));

    @Inject
    ConsumerTemplate consumerTemplate;

    @Test
    void getCamelContextMBean() {
        RestAssured.get("/q/jolokia/read/org.apache.camel:context=camel-1,type=context,name=\"camel-1\"")
                .then()
                .statusCode(200)
                .body(
                        "value.UptimeMillis", greaterThan(0),
                        "value.TotalRoutes", equalTo(1));
    }

    @Test
    void sendMessageToRoute() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"type\":\"exec\",\"mbean\":\"org.apache.camel:context=camel-1,type=context,name=\\\"camel-1\\\"\",\"operation\":\"sendStringBody(java.lang.String, java.lang.String)\",\"arguments\":[\"direct://start\",\"Hello World\"]}")
                .post("/q/jolokia/")
                .then()
                .statusCode(200)
                .body("status", equalTo(200));

        String message = consumerTemplate.receiveBody("seda:end", 10000, String.class);
        assertEquals("Hello World", message);
    }

    @ApplicationScoped
    public static class Routes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("seda:end");
        }
    }
}
