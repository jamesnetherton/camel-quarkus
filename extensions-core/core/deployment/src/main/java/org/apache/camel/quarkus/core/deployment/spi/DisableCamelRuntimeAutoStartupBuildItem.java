package org.apache.camel.quarkus.core.deployment.spi;

import io.quarkus.builder.item.MultiBuildItem;
import org.apache.camel.quarkus.core.CamelRuntime;

/**
 * Indicates that the {@link CamelRuntime} should not be started automatically.
 */
public final class DisableCamelRuntimeAutoStartupBuildItem extends MultiBuildItem {
}
