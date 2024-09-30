package org.apache.camel.quarkus.jolokia;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.camel.jolokia")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface JolokiaBuildTimeConfig {
    /**
     * Enables Jolokia support.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Kubernetes configuration.
     */
    Kubernetes kubernetes();

    interface Kubernetes {
        /**
         * Enables Kubernetes support.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
