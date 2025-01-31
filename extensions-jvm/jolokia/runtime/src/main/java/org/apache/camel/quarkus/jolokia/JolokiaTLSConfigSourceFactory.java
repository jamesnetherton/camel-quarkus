package org.apache.camel.quarkus.jolokia;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class JolokiaTLSConfigSourceFactory implements ConfigSourceFactory {
    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new ConfigSourceContext.ConfigSourceContextConfigSource(context))
                .withMapping(JolokiaRuntimeConfig.class)
                .withMappingIgnore("quarkus.**")
                .build();

        JolokiaRuntimeConfig jolokiaRuntimeConfig = config.getConfigMapping(JolokiaRuntimeConfig.class);
        File file = jolokiaRuntimeConfig.kubernetes().serviceCaCert();
        if (Files.exists(file.toPath())) {
            Map<String, String> properties = Map.of(
                    "quarkus.http.ssl.certificate.key-store-file", file.getAbsolutePath(),
                    "quarkus.http.ssl.certificate.trust-store-file", file.getAbsolutePath(),
                    "quarkus.http.auth.permission.jolokia.paths", "/q/jolokia/*",
                    "quarkus.http.auth.permission.jolokia.policy", "jolokia-cert-policy",
                    "quarkus.http.auth.policy.jolokia-cert-policy.roles-allowed", "jolokia");
            return Set.of(new PropertiesConfigSource(properties, "camel-quarkus-jolokia", 50));
        }

        return Collections.emptySet();
    }
}
