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

import io.quarkus.arc.Arc;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfig;
import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.core.tls.TlsConfigurationConverter.BridgedSSLContextParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for managing Quarkus TLS configurations as Camel SSLContextParameters beans.
 */
final class TlsRegistryHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TlsRegistryHelper.class);

    private TlsRegistryHelper() {
        // Utility class
    }

    /**
     * Get the Quarkus TLS configuration registry.
     *
     * @return the TLS registry, or null if not available
     */
    static TlsConfigurationRegistry getTlsRegistry() {
        return Arc.container()
                .select(TlsConfigurationRegistry.class)
                .orNull();
    }

    /**
     * Check if a TLS configuration name represents the default configuration.
     *
     * @param  name the configuration name
     * @return      true if this is the default config
     */
    static boolean isDefaultConfig(String name) {
        return "default".equals(name) || TlsConfig.DEFAULT_NAME.equals(name);
    }

    /**
     * Register a new TLS configuration as a Camel SSLContextParameters bean.
     * Throws an exception if a bean with the same name already exists.
     *
     * @param  camelContext          the Camel context
     * @param  config                the TLS registry configuration
     * @param  name                  the TLS configuration name
     * @param  tlsConfig             the TLS configuration
     * @param  vertx                 the Vert.x instance
     * @return                       the registered bean
     * @throws IllegalStateException if a bean with the same name already exists
     */
    static BridgedSSLContextParameters registerBean(
            CamelContext camelContext,
            TlsRegistryConfig config,
            String name,
            TlsConfiguration tlsConfig,
            Vertx vertx) {

        BridgedSSLContextParameters sslParams = TlsConfigurationConverter.convert(tlsConfig, name, vertx);

        if (isGlobal(config, name)) {
            LOG.info("Setting Quarkus default TLS as Camel global SSLContextParameters");
            camelContext.setSSLContextParameters(sslParams);
        } else {
            String beanName = isDefaultConfig(name) ? config.defaultBeanName() : name;
            registerBeanWithConflictCheck(camelContext, beanName, sslParams, name);
        }
        return sslParams;
    }

    /**
     * Whether a TLS configuration is bridged as the Camel global SSLContextParameters.
     */
    static boolean isGlobal(TlsRegistryConfig config, String name) {
        return isDefaultConfig(name) && config.quarkusDefaultAsGlobal();
    }

    /**
     * Register a bean in the Camel registry, checking for conflicts.
     *
     * @param  context               the Camel context
     * @param  beanName              the bean name to register
     * @param  sslParams             the SSL context parameters
     * @param  tlsConfigName         the original TLS config name (for error messages)
     * @throws IllegalStateException if a bean with the same name already exists
     */
    static void registerBeanWithConflictCheck(
            CamelContext context,
            String beanName,
            SSLContextParameters sslParams,
            String tlsConfigName) {

        SSLContextParameters existing = context.getRegistry()
                .lookupByNameAndType(beanName, SSLContextParameters.class);

        if (existing != null) {
            throw new IllegalStateException(String.format(
                    "SSLContextParameters bean with name '%s' already exists in the Camel registry. "
                            + "To use the Quarkus TLS registry configuration instead, either:%n"
                            + "  1. Remove or rename the existing SSLContextParameters bean, OR%n"
                            + "  2. Rename the Quarkus TLS configuration (quarkus.tls.%s.* → quarkus.tls.<new-name>.*)",
                    beanName, tlsConfigName));
        }

        LOG.info("Registering Quarkus TLS configuration '{}' as Camel SSLContextParameters bean", tlsConfigName);
        context.getRegistry().bind(beanName, SSLContextParameters.class, sslParams);
    }
}
