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
package org.apache.camel.quarkus.component.console;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsoleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsoleEnabledWithCamelMainPropertyTest {
    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("camel.main.dev-console-enabled", "true");

    @Inject
    CamelContext context;

    @Test
    void managementEndpointEnabled() {
        RestAssured.get("/q/camel/dev-console")
                .then()
                .statusCode(200);
    }

    @Test
    void devConsoleRegistryDiscoverable() {
        DevConsoleRegistry devConsoleRegistry = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        assertNotNull(devConsoleRegistry);
    }
}
