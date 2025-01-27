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
package org.apache.camel.quarkus.jolokia;

import java.io.IOException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.Handler;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.http.HttpRequestHandler;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.jolokia.server.core.restrictor.RestrictorFactory;
import org.jolokia.server.core.service.JolokiaServiceManagerFactory;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaServiceManager;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.service.jmx.LocalRequestHandler;
import org.jolokia.service.serializer.JolokiaSerializer;

@Recorder
public class CamelJolokiaRecorder {
    private static final Logger LOG = Logger.getLogger(JolokiaHandler.class);

    public Consumer<Route> route(Handler<RoutingContext> bodyHandler) {
        return new Consumer<Route>() {
            @Override
            public void accept(Route route) {
                route.handler(bodyHandler).produces("application/json");
            }
        };
    }

    public Handler<RoutingContext> getHandler(RuntimeValue<HttpRequestHandler> httpRequestHandler) {
        return new JolokiaHandler(httpRequestHandler.getValue());
    }

    public RuntimeValue<JolokiaContext> startJolokiaServiceManager(ShutdownContext shutdownContext) {
        StaticConfiguration configuration = new StaticConfiguration(ConfigKey.AGENT_ID,
                NetworkUtil.getAgentId(hashCode(), "camel-quarkus-jolokia"));
        String policyLocation = NetworkUtil.replaceExpression(configuration.getConfig(ConfigKey.POLICY_LOCATION));
        JolokiaServiceManager serviceManager = JolokiaServiceManagerFactory.createJolokiaServiceManager(
                configuration,
                new CamelQuarkusJolokiaLogHandler(),
                CamelQuarkusJolokiaRestrictor.resolve(policyLocation));
        serviceManager.addService(new JolokiaSerializer());
        serviceManager.addService(new LocalRequestHandler(1));
        shutdownContext.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                serviceManager.stop();
            }
        });
        return new RuntimeValue<>(serviceManager.start());
    }

    public RuntimeValue<HttpRequestHandler> createJolokiaHttpRequestHandler(RuntimeValue<JolokiaContext> jolokiaContext) {
        return new RuntimeValue<>(new HttpRequestHandler(jolokiaContext.getValue()));
    }

    public RuntimeValue<SecurityIdentityAugmentor> createSecurityIdentityAugmentor(JolokiaRuntimeConfig runtimeConfig) {
        return new RuntimeValue<>(new JolokiaSecurityIdentityAugmentor(runtimeConfig.kubernetes().clientPrincipal()));
    }

    public Supplier<TlsConfiguration> createSupplier(JolokiaRuntimeConfig runtimeConfig) {
        return new Supplier<TlsConfiguration>() {
            @Override
            public TlsConfiguration get() {
                return new TlsConfiguration() {
                    @Override
                    public KeyStore getKeyStore() {
                        System.out.println("====> " + runtimeConfig.kubernetes().clientPrincipal());
                        runtimeConfig.kubernetes().clientPrincipal().toString();
                        return null;
                    }

                    @Override
                    public KeyCertOptions getKeyStoreOptions() {
                        return null;
                    }

                    @Override
                    public KeyStore getTrustStore() {
                        return null;
                    }

                    @Override
                    public TrustOptions getTrustStoreOptions() {
                        return null;
                    }

                    @Override
                    public SSLOptions getSSLOptions() {
                        return null;
                    }

                    @Override
                    public SSLContext createSSLContext() throws Exception {
                        return null;
                    }

                    @Override
                    public Optional<String> getHostnameVerificationAlgorithm() {
                        return Optional.empty();
                    }

                    @Override
                    public boolean usesSni() {
                        return false;
                    }

                    @Override
                    public boolean reload() {
                        return false;
                    }
                };
            }
        };
    }

    static final class CamelQuarkusJolokiaRestrictor {
        static Restrictor resolve(String location) {
            try {
                Restrictor restrictor = RestrictorFactory.lookupPolicyRestrictor(location);
                if (restrictor != null) {
                    LOG.infof("Using access restrictor: %s", location);
                    return restrictor;
                } else {
                    LOG.infof("No access restrictor found at: %s, access to all MBeans is allowed", restrictor);
                    return new AllowAllRestrictor();
                }
            } catch (IOException e) {
                LOG.errorf(e,
                        "Error while accessing access restrictor at %s. Denying all access to MBeans for security reasons");
                return new DenyAllRestrictor();
            }
        }
    }

    static final class CamelQuarkusJolokiaLogHandler implements LogHandler {
        @Override
        public void debug(String s) {
            LOG.debug(s);
        }

        @Override
        public void info(String s) {
            LOG.info(s);
        }

        @Override
        public void error(String s, Throwable throwable) {
            LOG.error(s);
        }

        @Override
        public boolean isDebug() {
            return LOG.isDebugEnabled();
        }
    }
}
