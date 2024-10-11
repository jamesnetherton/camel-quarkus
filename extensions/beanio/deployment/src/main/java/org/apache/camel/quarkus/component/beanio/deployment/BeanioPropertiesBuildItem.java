package org.apache.camel.quarkus.component.beanio.deployment;

import java.util.Properties;

import io.quarkus.builder.item.SimpleBuildItem;

public final class BeanioPropertiesBuildItem extends SimpleBuildItem {
    private final Properties properties;

    public BeanioPropertiesBuildItem(Properties properties) {
        this.properties = properties;
    }

    public Properties getProperties() {
        return properties;
    }
}
