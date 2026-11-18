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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Navigate;
import org.apache.camel.Route;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restarts the endpoints that hold a copy of changed TLS material, and the routes using them.
 */
final class TlsRouteRestarter {
    private static final Logger LOG = LoggerFactory.getLogger(TlsRouteRestarter.class);

    private final CamelContext camelContext;
    private final Map<String, List<String>> endpointSslOptions;
    private final Map<String, List<String>> componentSslOptions;

    /**
     * @param camelContext        the Camel context
     * @param endpointSslOptions  the endpoint options of type SSLContextParameters, by component scheme
     * @param componentSslOptions the component options of type SSLContextParameters, by component scheme
     */
    TlsRouteRestarter(
            CamelContext camelContext,
            Map<String, List<String>> endpointSslOptions,
            Map<String, List<String>> componentSslOptions) {
        this.camelContext = camelContext;
        this.endpointSslOptions = endpointSslOptions;
        this.componentSslOptions = componentSslOptions;
    }

    /**
     * Restart the endpoints and routes using any of the given beans.
     *
     * @param changed the changed beans, compared by identity
     * @param global  the global SSLContextParameters, if it is one of the changed beans, otherwise null
     */
    void restart(Set<SSLContextParameters> changed, SSLContextParameters global) {
        Set<Endpoint> endpoints = affectedEndpoints(changed, global);
        if (endpoints.isEmpty()) {
            LOG.debug("No endpoint uses the updated TLS configurations");
            return;
        }

        Map<String, ServiceStatus> routes = affectedRoutes(endpoints);
        // Endpoint URIs are not logged, since they can carry credentials
        LOG.info("Restarting routes {} and {} endpoint(s) to apply updated TLS configurations",
                routes.keySet(), endpoints.size());

        RouteController routeController = camelContext.getRouteController();
        List<String> running = routes.entrySet().stream()
                .filter(route -> route.getValue() == ServiceStatus.Started || route.getValue() == ServiceStatus.Suspended)
                .map(Map.Entry::getKey)
                .toList();

        try {
            drain(running);
            for (String routeId : running) {
                routeController.stopRoute(routeId);
            }
        } catch (Exception e) {
            LOG.error("Failed to stop routes {} to apply updated TLS configurations", running, e);
        }

        for (Endpoint endpoint : endpoints) {
            try {
                ServiceHelper.stopService(endpoint);
                ServiceHelper.startService(endpoint);
            } catch (Exception e) {
                LOG.error("Failed to restart a '{}' endpoint to apply updated TLS configurations",
                        scheme(endpoint), e);
            }
        }

        for (String routeId : running) {
            try {
                routeController.startRoute(routeId);
                if (routes.get(routeId) == ServiceStatus.Suspended) {
                    routeController.suspendRoute(routeId);
                }
            } catch (Exception e) {
                LOG.error("Failed to restore route {} after applying updated TLS configurations", routeId, e);
            }
        }
    }

    /**
     * Wait for the in-flight exchanges of all routes in one go, rather than one route after another.
     */
    private void drain(List<String> routeIds) throws Exception {
        List<RouteStartupOrder> routes = new ArrayList<>();
        for (RouteStartupOrder order : camelContext.getCamelContextExtension().getRouteStartupOrder()) {
            if (routeIds.contains(order.getRoute().getId())) {
                routes.add(order);
            }
        }
        if (!routes.isEmpty()) {
            ShutdownStrategy shutdownStrategy = camelContext.getShutdownStrategy();
            shutdownStrategy.shutdown(camelContext, routes, shutdownStrategy.getTimeout(), shutdownStrategy.getTimeUnit());
        }
    }

    /**
     * The started endpoints whose SSLContextParameters option is one of the changed beans. When the endpoint option is
     * not set, the component option, and then the global SSLContextParameters if the component uses it.
     */
    Set<Endpoint> affectedEndpoints(Set<SSLContextParameters> changed, SSLContextParameters global) {
        Set<Endpoint> affected = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Endpoint registered : camelContext.getEndpoints()) {
            Endpoint endpoint = unwrap(registered);
            try {
                if (uses(endpoint, changed, global)) {
                    affected.add(endpoint);
                }
            } catch (Exception e) {
                // One endpoint that cannot be inspected must not prevent the others from being restarted
                LOG.warn("Failed to determine whether a '{}' endpoint uses the updated TLS configurations, "
                        + "it is not restarted", scheme(endpoint), e);
            }
        }
        return affected;
    }

    private boolean uses(Endpoint endpoint, Set<SSLContextParameters> changed, SSLContextParameters global) {
        if (endpoint instanceof StatefulService service && !service.isStarted()) {
            return false;
        }
        String scheme = scheme(endpoint);
        if (scheme == null) {
            return false;
        }
        Component component = endpoint instanceof DefaultEndpoint defaultEndpoint
                ? defaultEndpoint.getComponent()
                : camelContext.hasComponent(scheme);
        if (component == null) {
            return false;
        }

        // A component can be registered under another name than the scheme its options are known by
        String optionsKey = scheme;
        if (!endpointSslOptions.containsKey(scheme) && !componentSslOptions.containsKey(scheme)
                && component.getDefaultName() != null) {
            optionsKey = component.getDefaultName();
        }

        Object value = firstValue(component.getEndpointPropertyConfigurer(), endpoint,
                endpointSslOptions.get(optionsKey));
        if (value == null) {
            value = firstValue(component.getComponentPropertyConfigurer(), component,
                    componentSslOptions.get(optionsKey));
        }
        if (value == null && global != null && component instanceof SSLContextParametersAware aware
                && aware.isUseGlobalSslContextParameters()) {
            value = global;
        }
        return value != null && changed.contains(value);
    }

    /**
     * The endpoint behind an interceptor, which is what the registry holds for an intercepted endpoint.
     */
    private static Endpoint unwrap(Endpoint endpoint) {
        while (endpoint instanceof InterceptSendToEndpoint intercepted && intercepted.getOriginalEndpoint() != null
                && intercepted.getOriginalEndpoint() != endpoint) {
            endpoint = intercepted.getOriginalEndpoint();
        }
        return endpoint;
    }

    private static String scheme(Endpoint endpoint) {
        return StringHelper.before(endpoint.getEndpointUri(), ":");
    }

    private static Object firstValue(PropertyConfigurer configurer, Object target, List<String> options) {
        if (options == null || !(configurer instanceof PropertyConfigurerGetter getter)) {
            return null;
        }
        for (String option : options) {
            Object value = getter.getOptionValue(target, option, false);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * The routes that consume from an affected endpoint, or send to one through a static send processor, with their
     * current status.
     */
    Map<String, ServiceStatus> affectedRoutes(Set<Endpoint> endpoints) {
        Map<String, ServiceStatus> routes = new LinkedHashMap<>();
        for (Route route : camelContext.getRoutes()) {
            if (endpoints.contains(unwrap(route.getEndpoint()))
                    || sendsTo(route.navigate(), endpoints, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                routes.put(route.getId(), camelContext.getRouteController().getRouteStatus(route.getId()));
            }
        }
        return routes;
    }

    private static boolean sendsTo(Navigate<?> navigate, Set<Endpoint> endpoints, Set<Object> visited) {
        List<?> next = navigate.next();
        if (next == null) {
            return false;
        }
        for (Object processor : next) {
            if (processor == null || !visited.add(processor)) {
                continue;
            }
            if (processor instanceof EndpointAware aware && aware.getEndpoint() != null
                    && endpoints.contains(unwrap(aware.getEndpoint()))) {
                return true;
            }
            if (processor instanceof Navigate<?> child && sendsTo(child, endpoints, visited)) {
                return true;
            }
        }
        return false;
    }
}
