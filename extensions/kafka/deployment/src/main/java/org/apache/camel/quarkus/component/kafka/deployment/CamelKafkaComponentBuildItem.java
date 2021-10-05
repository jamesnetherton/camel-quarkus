package org.apache.camel.quarkus.component.kafka.deployment;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;
import org.apache.camel.component.kafka.KafkaComponent;

final class CamelKafkaComponentBuildItem extends SimpleBuildItem {

    private final RuntimeValue<KafkaComponent> value;

    public CamelKafkaComponentBuildItem(RuntimeValue<KafkaComponent> value) {
        this.value = value;
    }

    public RuntimeValue<KafkaComponent> getValue() {
        return value;
    }
}
