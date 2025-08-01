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
package org.apache.camel.quarkus.core.dataformat;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "camel.dataformat")
public interface CamelDataFormatRuntimeConfig {
    /**
     * Camel data format configuration.
     *
     * The format of the configuration is as follows.
     *
     * [source,properties]
     * ----
     * camel.dataformat.<name>.<property> = value
     * ----
     *
     * For example.
     * [source,properties]
     * ----
     * camel.dataformat.beanio.stream-name = test-stream
     * camel.dataformat.beanio.mapping = test-mapping.xml
     * ----
     */
    @WithParentName
    Map<String, Map<String, String>> dataFormatConfigs();
}
