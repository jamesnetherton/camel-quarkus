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
package org.apache.camel.quarkus.core.tls;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Runtime configuration for the Camel Quarkus TLS Registry integration.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.camel.tls-registry")
public interface TlsRegistryConfig {

    String DEFAULT_BEAN_NAME = "defaultSslContextParameters";

    /**
     * Enable automatic conversion and registration of Quarkus TLS configurations
     * as Camel SSLContextParameters beans.
     *
     * When enabled, the default TLS configuration and any named configurations will
     * be automatically discovered and registered as beans in the Camel registry.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Whether to set the Quarkus default TLS configuration as Camel's global
     * SSLContextParameters.
     *
     * When true, if a default TLS configuration exists (`quarkus.tls.*`), it will
     * be converted and set as the global SSL context via `CamelContext.setSSLContextParameters()`.
     * Components can opt in to this global context using `useGlobalSslContextParameters=true`.
     */
    @WithName("quarkus-default-as-global")
    @WithDefault("false")
    boolean quarkusDefaultAsGlobal();

    /**
     * The name to use when registering the default TLS configuration as a bean.
     *
     * Only applicable if the default configuration is not set as global
     * (i.e., `quarkus-default-as-global=false`).
     */
    @WithDefault(DEFAULT_BEAN_NAME)
    String defaultBeanName();

    /**
     * Whether to restart the routes using a TLS configuration when its certificates are updated.
     *
     * New connections use the updated key and trust material whether or not this is enabled. When enabled, the
     * endpoints that hold their own copy of the previous material are restarted, together with the routes using them.
     * When the trust material changes, every endpoint using the configuration is restarted, so that a consumer stops
     * resuming sessions established before the change.
     */
    @WithDefault("true")
    boolean reloadOnCertificateUpdate();

    /**
     * Delay in milliseconds before restarting routes after a certificate update. A further update within the delay
     * postpones the restart, so that updates arriving together cause a single restart.
     */
    @WithDefault("2000")
    long reloadCertificateDelay();
}
