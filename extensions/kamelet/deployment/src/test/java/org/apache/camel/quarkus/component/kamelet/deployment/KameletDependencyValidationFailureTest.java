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

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KameletDependencyValidationFailureTest {
    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .assertException(throwable -> {
                assertInstanceOf(IllegalStateException.class, throwable);
                String message = throwable.getMessage();
                assertTrue(message.contains("Required Kamelet dependencies are not present"));
                assertTrue(message.contains("org.apache.camel.quarkus:camel-quarkus-timer"));
                assertFalse(message.contains("org.apache.camel.quarkus:camel-quarkus-log"));
                assertFalse(message.contains("io.quarkus:quarkus-smallrye-health"));
            })
            .setForcedDependencies(List.of(
                    Dependency.of("org.apache.camel.quarkus", "camel-quarkus-log"),
                    Dependency.of("io.quarkus", "quarkus-smallrye-health", Version.getVersion())))
            .overrideConfigKey("quarkus.camel.kamelet.identifiers", "test")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("test.kamelet.yaml",
                    "kamelets/test.kamelet.yaml"));

    @Test
    void dependencyValidationThrowsException() {
        // Nothing to test - app startup failed
    }
}
