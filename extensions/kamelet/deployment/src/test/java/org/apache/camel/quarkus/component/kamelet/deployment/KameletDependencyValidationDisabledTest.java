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
package org.apache.camel.quarkus.component.kamelet.deployment;

import java.util.List;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KameletDependencyValidationDisabledTest {
    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.camel.kamelet.identifiers", "test")
            .overrideConfigKey("quarkus.camel.kamelet.validate-dependencies", "false")
            .setForcedDependencies(List.of(Dependency.of("org.apache.camel.quarkus", "camel-quarkus-timer")))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("test.kamelet.yaml",
                    "kamelets/test.kamelet.yaml"));

    @Inject
    CamelContext camelContext;

    @Test
    void dependencyValidationDisabledAllowsAppStartup() {
        assertEquals(ServiceStatus.Started, camelContext.getStatus());
    }
}
