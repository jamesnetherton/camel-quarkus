package org.apache.camel.quarkus.test.containers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.testcontainers.utility.DockerImageName;

/**
 * Utility for resolving project container image names.
 *
 * The image names are defined as maven properties in the root project pom.xml. Each property is suffixed with
 * .container.image
 *
 * The container image property names and values are written to a file named container-image.properties by a Groovy
 * script at build time. This is
 * loaded and interrogated at test time.
 *
 * The default image names can be overridden via system properties. For example:
 *
 * mvn clean test -Dinfinispan.container.image=infinispan/server:latest
 * -Dactivemq-classic.container.image=rmohr/activemq:latest
 */
public enum TestContainer {
    ACTIVEMQ,
    ACTIVEMQ_CLASSIC,
    ARANGODB,
    AZURITE,
    CALCULATOR_WS,
    CASSANDRA,
    CONSUL,
    COUCHBASE,
    COUCHDB,
    DEBEZIUM,
    DERBY,
    ELASTICSEARCH,
    FHIR,
    FHIR_DSTU,
    GOOGLE_CLOUD_SDK,
    GOOGLE_STORAGE,
    GREENMAIL,
    INFINISPAN,
    INFLUXDB,
    KAFKA,
    KUDU,
    LOCALSTACK,
    LRA_COORDINATOR,
    MINIO,
    MONGODB,
    NATS,
    OPENSSH_SERVER,
    POSTGRES,
    RABBITMQ,
    SOLR,
    SPLUNK,
    TINYPROXY;

    private static final Properties properties;

    static {
        try (InputStream stream = TestContainer.class.getResourceAsStream("/container-image.properties")) {
            properties = new Properties();
            properties.load(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DockerImageName getImageName() {
        String key = String.format("%s.container.image", name().toLowerCase().replaceAll("_", "-"));
        String value = System.getProperty(key, properties.getProperty(key));
        return DockerImageName.parse(value);
    }
}
