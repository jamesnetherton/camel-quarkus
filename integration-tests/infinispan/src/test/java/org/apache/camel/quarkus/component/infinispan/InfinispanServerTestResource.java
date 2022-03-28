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
package org.apache.camel.quarkus.component.infinispan;

import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.camel.util.ObjectHelper;

import java.util.Map;

public class InfinispanServerTestResource implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {
    private Map<String, String> devServicesProperties;

    @Override
    public void setIntegrationTestContext(DevServicesContext context) {
        devServicesProperties = context.devServicesProperties();
    }

    @Override
    public Map<String, String> start() {
            if (ObjectHelper.isNotEmpty(devServicesProperties)) {
                // Create 2 sets of configuration to test scenarios:
                // - Quarkus Infinispan client bean being autowired into the Camel Infinispan component
                // - Component configuration where the Infinispan client is managed by Camel (E.g Infinispan client autowiring disabled)
                devServicesProperties.put("camel.component.infinispan.autowired-enabled", "false");
                devServicesProperties.put("camel.component.infinispan.hosts", devServicesProperties.get("quarkus.infinispan-client.server-list"));
                devServicesProperties.put("camel.component.infinispan.username", devServicesProperties.get("quarkus.infinispan-client.auth-username"));
                devServicesProperties.put("camel.component.infinispan.password", devServicesProperties.get("quarkus.infinispan-client.auth-password"));
                return devServicesProperties;
            } else {
                throw new IllegalStateException("Dev services properties supplied to " + this.getClass().getSimpleName() + " was null or empty");
            }
    }

    @Override
    public void stop() {
    }
}
