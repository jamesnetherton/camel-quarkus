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
package org.apache.camel.quarkus.jolokia.config;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.camel.jolokia")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JolokiaRuntimeConfig {
    /**
     * Arbitrary Jolokia configuration options. These are described at the
     * https://jolokia.org/reference/html/manual/agents.html[Jolokia documentation].
     * Options can be configured like `quarkus.camel.jolokia.additional-properties."debug"=true`.
     */
    Map<String, String> additionalProperties();

    /**
     * When `true`, a Jolokia restrictor is registered that limits MBean read, write and operation execution to the
     * following MBean domains.
     *
     * * org.apache.camel
     * * java.lang
     * * java.nio
     *
     * Note that this option has no effect if `quarkus.camel.jolokia.additional-properties."restrictorClass"` is set.
     */
    @WithDefault("true")
    boolean registerCamelRestrictor();
}
