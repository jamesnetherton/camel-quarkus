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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfig;
import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.core.tls.TlsConfigurationConverter.BridgedSSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime recorder for registering Quarkus TLS configurations as Camel SSLContextParameters beans.
 */
@Recorder
public class TlsRegistryRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(TlsRegistryRecorder.class);

    private final RuntimeValue<TlsRegistryConfig> tlsRegistryConfigRuntimeValue;
    private final RuntimeValue<TlsConfig> tlsConfigRuntimeValue;

    public TlsRegistryRecorder(RuntimeValue<TlsRegistryConfig> tlsRegistryConfigRuntimeValue,
            RuntimeValue<TlsConfig> tlsConfigRuntimeValue) {
        this.tlsRegistryConfigRuntimeValue = tlsRegistryConfigRuntimeValue;
        this.tlsConfigRuntimeValue = tlsConfigRuntimeValue;
    }

    /**
     * Register Quarkus TLS configurations as Camel SSLContextParameters beans.
     * Called at RUNTIME_INIT after the Camel context is created.
     *
     * @param camelContextRuntimeValue the Camel context
     * @param vertxRuntimeValue        the Vert.x instance, used to read the key material of TLS configurations
     * @param endpointSslOptions       the endpoint options of type SSLContextParameters, by component scheme
     * @param componentSslOptions      the component options of type SSLContextParameters, by component scheme
     */
    public void registerTlsConfigurations(RuntimeValue<CamelContext> camelContextRuntimeValue,
            RuntimeValue<Vertx> vertxRuntimeValue,
            Map<String, List<String>> endpointSslOptions,
            Map<String, List<String>> componentSslOptions) {
        TlsRegistryConfig config = tlsRegistryConfigRuntimeValue.getValue();

        if (!config.enabled()) {
            LOG.debug("TLS registry integration is disabled");
            return;
        }

        // Access TLS registry at runtime
        TlsConfigurationRegistry tlsRegistry = TlsRegistryHelper.getTlsRegistry();
        if (tlsRegistry == null) {
            LOG.debug("TLS configuration registry not available (no TLS configured)");
            return;
        }

        CamelContext camelContext = camelContextRuntimeValue.getValue();
        Vertx vertx = vertxRuntimeValue.getValue();
        TlsCertificateReloadObserver observer = Arc.container().instance(TlsCertificateReloadObserver.class).get();
        observer.setComponentSslOptions(endpointSslOptions, componentSslOptions);

        // Process default TLS configuration
        Optional<TlsConfiguration> defaultTls = tlsRegistry.getDefault();
        if (defaultTls.isPresent()) {
            register(observer, camelContext, config, "default", defaultTls.get(), vertx);
        }

        // Process all named TLS configurations
        TlsConfig tlsConfig = tlsConfigRuntimeValue.getValue();
        for (String name : tlsConfig.namedCertificateConfig().keySet()) {
            Optional<TlsConfiguration> namedTls = tlsRegistry.get(name);
            if (namedTls.isPresent()) {
                LOG.debug("Processing named TLS configuration '{}'", name);
                register(observer, camelContext, config, name, namedTls.get(), vertx);
            }
        }
    }

    private static void register(
            TlsCertificateReloadObserver observer,
            CamelContext camelContext,
            TlsRegistryConfig config,
            String name,
            TlsConfiguration tlsConfiguration,
            Vertx vertx) {
        BridgedSSLContextParameters bean = TlsRegistryHelper.registerBean(camelContext, config, name, tlsConfiguration,
                vertx);
        observer.register(name, bean, TlsRegistryHelper.isGlobal(config, name));
    }
}
