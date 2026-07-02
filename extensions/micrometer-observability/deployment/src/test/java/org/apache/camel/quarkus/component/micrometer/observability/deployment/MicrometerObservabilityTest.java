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

package org.apache.camel.quarkus.component.micrometer.observability.deployment;

import java.util.Set;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.micrometer.observability.MicrometerObservabilityTracer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MicrometerObservabilityTest {

    private static final String INCLUDE_PATTERNS = "netty-http:*,netty-http:/prefix/.*";
    private static final String EXCLUDE_PATTERNS = "platform-http:*,platform-http:/prefix/.*";

    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.camel.micrometer-observability.include-patterns", INCLUDE_PATTERNS)
            .overrideConfigKey("quarkus.camel.micrometer-observability.exclude-patterns", EXCLUDE_PATTERNS)
            .overrideConfigKey("quarkus.camel.micrometer-observability.trace-processors", "true")
            .overrideConfigKey("quarkus.camel.micrometer-observability.trace-headers-inclusion", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    CamelContext context;

    @Test
    public void camelMicrometerObservabilityTracerRegistryBeanNotNull() {
        Set<MicrometerObservabilityTracer> tracers = context.getRegistry().findByType(MicrometerObservabilityTracer.class);
        assertEquals(1, tracers.size());

        MicrometerObservabilityTracer tracer = tracers.iterator().next();
        assertInstanceOf(MicrometerObservabilityTracer.class, tracer);
        assertEquals(INCLUDE_PATTERNS, tracer.getIncludePatterns());
        assertEquals(EXCLUDE_PATTERNS, tracer.getExcludePatterns());
        assertTrue(tracer.isTraceProcessors());
        assertTrue(tracer.isTraceHeadersInclusion());
    }
}
