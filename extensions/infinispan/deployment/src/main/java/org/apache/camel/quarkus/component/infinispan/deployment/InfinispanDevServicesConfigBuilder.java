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

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Suppress Quarkus Infinispan creation of 'zero config' RemoteCacheManager bean in prod mode if dev services is
 * enabled. It causes problems if the application does not use quarkus.infinispan properties to configure the client.
 * For more details see <a href=
 * "https://github.com/apache/camel-quarkus/issues/5965">https://github.com/apache/camel-quarkus/issues/5965</a>.
 */
public class InfinispanDevServicesConfigBuilder implements SmallRyeConfigBuilderCustomizer {
    private static final String CONFIG_SOURCE = "camel-quarkus-infinispan";
    private static final String CREATE_DEFAULT_CLIENT_PROP_NAME = "quarkus.infinispan-client.devservices.create-default-client";

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withSources(
                new PropertiesConfigSource(
                        Map.of(CREATE_DEFAULT_CLIENT_PROP_NAME, "false"), CONFIG_SOURCE, 50));
    }
}
