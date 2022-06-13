package org.apache.camel.quarkus.component.kafka.test;

import java.util.Collections;
import java.util.Optional;

import io.quarkus.test.common.ArtifactLauncher.InitContext.DevServicesLaunchResult;
import io.quarkus.test.common.DefaultNativeImageLauncher;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class CamelQuarkusNativeImageLauncher extends DefaultNativeImageLauncher {

    @Override
    public void init(NativeImageInitContext initContext) {
        super.init(initContext);

        Config config = ConfigProvider.getConfig();
        DevServicesLaunchResult result = initContext.getDevServicesLaunchResult();

        String kafkaBootstrapServers = result.properties().get("kafka.bootstrap.servers");
        Optional<String> camelKafkaBrokers = config.getOptionalValue("camel.component.kafka.brokers", String.class);
        if (kafkaBootstrapServers != null && camelKafkaBrokers.isEmpty()) {
            includeAsSysProps(Collections.singletonMap("camel.component.kafka.brokers", kafkaBootstrapServers));
        }
    }
}
