package org.apache.camel.quarkus.component.google.bigquery.it;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class GoogleBigqueryTestResource implements QuarkusTestResourceLifecycleManager {
    private static final DockerImageName BIGQUERY_IMAGE_NAME = DockerImageName.parse("ghcr.io/goccy/bigquery-emulator:0.1.2");
    private static final int BIGQUERY_PORT = 9050;
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(BIGQUERY_IMAGE_NAME)
                .withClasspathResourceMapping("/data.yml", "/data.yml", BindMode.READ_ONLY)
                .withExposedPorts(BIGQUERY_PORT)
                .withCommand("/bin/bigquery-emulator", "--project", "test", "--data-from-yaml", "/data.yml")
                .waitingFor(Wait.forListeningPort());

        container.start();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> options = new HashMap<>();
        options.put("google.bigquery.host",
                String.format("http://%s:%d", container.getHost(), container.getMappedPort(BIGQUERY_PORT)));
        return options;
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
