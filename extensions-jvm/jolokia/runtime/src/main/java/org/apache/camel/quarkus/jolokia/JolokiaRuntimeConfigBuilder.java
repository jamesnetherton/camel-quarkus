package org.apache.camel.quarkus.jolokia;

import java.util.Map;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

public class JolokiaRuntimeConfigBuilder implements ConfigBuilder {
    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        return builder.withSources(
                new PropertiesConfigSource(Map.of(
                        "quarkus.http.ssl.certificate.key-store-file", "/Users/james/Downloads/localhost.p12",
                        "quarkus.http.ssl.certificate.key-store-password", "localhost",
                        "quarkus.http.ssl.certificate.trust-store-file", "/Users/james/Downloads/localhost.p12",
                        "quarkus.http.ssl.certificate.trust-store-password", "localhost",
                        "quarkus.http.auth.permission.jolokia.paths", "/q/jolokia/*",
                        "quarkus.http.auth.policy.jolokia.roles-allowed", "jolokia",
                        "quarkus.http.auth.permission.jolokia.policy", "authenticated"),
                        "camel-quarkus-jolokia", 50));
    }
}

/*
quarkus.http.ssl.certificate.key-store-file=/Users/james/Downloads/localhost.p12
quarkus.http.ssl.certificate.key-store-password=localhost
quarkus.http.ssl.certificate.trust-store-file=/Users/james/Downloads/localhost.p12
quarkus.http.ssl.certificate.trust-store-password=localhost
quarkus.http.ssl.client-auth=required
quarkus.http.auth.permission.default.paths=/q/jolokia/*
quarkus.http.auth.permission.default.policy=authenticated
quarkus.http.insecure-requests=disabled
quarkus.http.auth.certificate-role-properties=cert-role-mappings.properties
 */
