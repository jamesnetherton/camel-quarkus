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
package org.apache.camel.quarkus.component.cxf.soap.ssl.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

// Tests require restart of Quarkus to avoid persisting of global ssl context.
@QuarkusTest
@TestProfile(CxfSoapSslTest.class)
public class CxfSoapSslTest implements QuarkusTestProfile {

    // Test is ported from SslTest in Camel-spring-boot/components-starter/camel-cxf-soap-starter
    @Test
    public void testInvokingTrustedRoute() {
        RestAssured.given()
                .body("ssl")
                .post("/cxf-soap/ssl/trusted/local")
                .then()
                .statusCode(201)
                .body(equalTo("Hello ssl!"));
    }

    // Test is ported from SslTest in Camel-spring-boot/components-starter/camel-cxf-soap-starter
    @Test
    public void testInvokingUntrustedRoute() {
        RestAssured.given()
                .body("ssl")
                .post("/cxf-soap/ssl/untrusted/local")
                .then()
                .statusCode(500)
                .body(containsString("signature check failed"));
    }

    // Test is ported from SslTest in Camel-spring-boot/components-starter/camel-cxf-soap-starter
    @Test
    public void testInvokingNotrustRoute() {
        RestAssured.given()
                .body("ssl")
                .post("/cxf-soap/ssl/notrust")
                .then()
                .statusCode(500)
                .body(containsString("unable to find valid certification path to requested target"));
    }
}
