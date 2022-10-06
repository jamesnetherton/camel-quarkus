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
package org.apache.camel.quarkus.component.http.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(HttpTestResource.class)
class HttpTest {

    private static final String[] HTTP_COMPONENTS = new String[] { "http", "netty-http", "vertx-http" };

    @ParameterizedTest
    @MethodSource("getHttpComponentNames")
    public void transferException(String component) {
        RestAssured
                .given()
                .queryParam("test-port", getPort())
                .when()
                .get("/test/client/{component}/serialized/exception", component)
                .then()
                .statusCode(200)
                .body(is("java.lang.IllegalStateException"));
    }

    private Integer getPort() {
        return getPort("camel.netty-http.test-port");
    }

    private Integer getPort(String configKey) {
        return ConfigProvider.getConfig().getValue(configKey, Integer.class);
    }

    private static String[] getHttpComponentNames() {
        return HTTP_COMPONENTS;
    }
}
