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
package org.apache.camel.quarkus.component.infinispan.deployment;

import java.util.logging.Level;

import io.quarkus.test.QuarkusDevModeTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteComponent;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.quarkus.component.infinispan.deployment.CamelStartupObserver.LOG_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanDevModeWithComponentBeanTest {
    @RegisterExtension
    static final QuarkusDevModeTest TEST = new QuarkusDevModeTest()
            .setLogRecordPredicate(record -> record.getLevel().equals(Level.INFO))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CamelStartupObserver.class, InfinispanComponentProducer.class));

    @Test
    void defaultInfinispanClientNotProduced() {
        assertTrue(TEST.getLogRecords()
                .stream()
                .anyMatch(record -> record.getMessage().contains(LOG_MESSAGE + "false")));
    }

    @ApplicationScoped
    static class InfinispanComponentProducer {
        @Produces
        InfinispanComponent infinispanComponent() {
            return new InfinispanRemoteComponent();
        }
    }
}
