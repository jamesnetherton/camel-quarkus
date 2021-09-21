package org.apache.camel.quarkus.core.deployment;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "camel", phase = ConfigPhase.BUILD_TIME)
public class CamelBuildTimeConfig {

    /**
     * Live reload configuration options for Camel in dev mode.
     */
    @ConfigItem
    public LiveReloadConfig liveReload;

    @ConfigGroup
    public static class LiveReloadConfig {
        /**
         * Whether dev mode support for live reloading of Camel routes is enabled.
         *
         * Note that this configuration property does not enable / disable Quarkus live reloading entirely. It merely
         * controls whether the Camel Quarkus specific {@code HotReplacementSetup} implementation is active.
         */
        @ConfigItem(defaultValue = "true")
        public boolean enabled;
    }
}
