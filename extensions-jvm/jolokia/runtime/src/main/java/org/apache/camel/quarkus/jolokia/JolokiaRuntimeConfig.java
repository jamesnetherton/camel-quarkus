package org.apache.camel.quarkus.jolokia;

import java.io.File;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.camel.jolokia")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JolokiaRuntimeConfig {
    /**
     * Kubernetes configuration.
     */
    Kubernetes kubernetes();

    interface Kubernetes {
        /**
         * Absolute path of the CA certificate the Jolokia should use for client authentication.
         */
        @WithDefault("/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt")
        File serviceCaCert();

        /**
         * The principal which must be given in a client certificate to allow access to Jolokia.
         */
        String clientPrincipal();
    }
}
