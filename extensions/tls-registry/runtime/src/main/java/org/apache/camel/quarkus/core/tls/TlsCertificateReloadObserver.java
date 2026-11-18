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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.quarkus.tls.CertificateUpdatedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.core.tls.TlsConfigurationConverter.BridgedSSLContextParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observes certificate update events from the Quarkus TLS registry.
 *
 * When the key or trust material of a bridged TLS configuration changed, the live key and trust managers of its
 * SSLContextParameters bean switch to the new material. When {@code reload-on-certificate-update} is enabled, the
 * endpoints holding a copy of the previous material, and the routes using them, are then restarted after a debounce
 * delay. When the trust material changed, every endpoint using the bean is restarted, so that a server stops resuming
 * sessions that were authenticated against the previous trust material.
 */
@ApplicationScoped
public class TlsCertificateReloadObserver {
    private static final Logger LOG = LoggerFactory.getLogger(TlsCertificateReloadObserver.class);

    @Inject
    CamelContext camelContext;

    @Inject
    TlsRegistryConfig config;

    private final Map<String, BridgedSSLContextParameters> beans = new ConcurrentHashMap<>();
    // Configuration name to whether its trust material changed
    private final Map<String, Boolean> pendingRestarts = new LinkedHashMap<>();
    private volatile String globalConfigurationName;
    private volatile TlsRouteRestarter restarter;
    private ScheduledFuture<?> pendingRestart;
    private ScheduledExecutorService scheduler;

    /**
     * Register the bean bridged from a TLS configuration.
     */
    void register(String name, BridgedSSLContextParameters bean, boolean global) {
        String key = key(name);
        beans.put(key, bean);
        if (global) {
            globalConfigurationName = key;
        }
    }

    /**
     * Set the SSLContextParameters options of the components, used to find the endpoints using a bean.
     */
    void setComponentSslOptions(Map<String, List<String>> endpointSslOptions, Map<String, List<String>> componentSslOptions) {
        restarter = new TlsRouteRestarter(camelContext, endpointSslOptions, componentSslOptions);
    }

    void onCertificateUpdated(@Observes CertificateUpdatedEvent event) {
        String key = key(event.name());
        BridgedSSLContextParameters bean = beans.get(key);
        if (bean == null) {
            LOG.debug("TLS configuration '{}' is not bridged to Camel, ignoring its certificate update", event.name());
            return;
        }

        TlsLiveMaterial.Changes changes;
        try {
            changes = bean.material().refresh();
        } catch (Exception e) {
            LOG.warn("Failed to read the updated key or trust material of TLS configuration '{}', keeping the current one",
                    event.name(), e);
            return;
        }

        if (!changes.any()) {
            LOG.debug("The key and trust material of TLS configuration '{}' did not change", event.name());
            return;
        }

        LOG.info("The key or trust material of TLS configuration '{}' changed, new connections use it", event.name());
        if (config.reloadOnCertificateUpdate()) {
            scheduleRestart(key, changes.trust());
        }
    }

    /**
     * Schedule the restart of the endpoints and routes using a changed configuration. A further change within the
     * delay postpones the restart, so that changes arriving together are applied in one restart.
     */
    private synchronized void scheduleRestart(String key, boolean trustChanged) {
        pendingRestarts.merge(key, trustChanged, Boolean::logicalOr);
        if (pendingRestart != null) {
            pendingRestart.cancel(false);
        }
        pendingRestart = getScheduler().schedule(this::restart, config.reloadCertificateDelay(), TimeUnit.MILLISECONDS);
        LOG.debug("Scheduled the restart for TLS configurations {} in {}ms", pendingRestarts.keySet(),
                config.reloadCertificateDelay());
    }

    private void restart() {
        Map<String, Boolean> keys;
        synchronized (this) {
            keys = new LinkedHashMap<>(pendingRestarts);
            pendingRestarts.clear();
            pendingRestart = null;
        }

        TlsRouteRestarter currentRestarter = restarter;
        if (currentRestarter == null) {
            return;
        }

        // A bean whose key or trust material was never copied is only used through its live managers. A trust change
        // still needs new SSLContexts, since a server accepts the session tickets issued by its context without
        // checking the client certificate again
        Set<SSLContextParameters> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        SSLContextParameters global = null;
        for (Map.Entry<String, Boolean> entry : keys.entrySet()) {
            String key = entry.getKey();
            BridgedSSLContextParameters bean = beans.get(key);
            if (bean != null && (entry.getValue() || bean.material().materialHandedOut())) {
                changed.add(bean);
                if (key.equals(globalConfigurationName)) {
                    global = bean;
                }
            }
        }
        if (changed.isEmpty()) {
            return;
        }

        try {
            currentRestarter.restart(changed, global);
        } catch (Exception e) {
            LOG.error("Failed to restart the endpoints using TLS configurations {}", keys.keySet(), e);
        }
    }

    private synchronized ScheduledExecutorService getScheduler() {
        if (scheduler == null) {
            scheduler = camelContext.getExecutorServiceManager()
                    .newScheduledThreadPool(this, "CamelQuarkusTlsReload", 1);
        }
        return scheduler;
    }

    @PreDestroy
    synchronized void cleanup() {
        if (pendingRestart != null) {
            pendingRestart.cancel(false);
        }
        if (scheduler != null) {
            camelContext.getExecutorServiceManager().shutdown(scheduler);
        }
    }

    private static String key(String name) {
        return TlsRegistryHelper.isDefaultConfig(name) ? "default" : name;
    }
}
