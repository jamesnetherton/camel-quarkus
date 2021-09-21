package org.apache.camel.quarkus.core.devmode;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.is;

public class CamelLiveReloadDisabledTest {

    @RegisterExtension
    static final QuarkusDevModeTest CONFIG = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(CamelLiveReloadResource.class)
                    .addAsResource(applicationProperties(), "application.properties"));

    @Test
    public void camelHotReplacementObserverBeanIsNotRegistered() {
        RestAssured.get("/camel/devmode/observer/registered")
                .then()
                .statusCode(200)
                .body(is("false"));
    }

    public static Asset applicationProperties() {
        Writer writer = new StringWriter();

        Properties props = new Properties();
        props.setProperty("quarkus.banner.enabled", "false");
        props.setProperty("quarkus.camel.live-reload.enabled", "false");

        try {
            props.store(writer, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new StringAsset(writer.toString());
    }
}
