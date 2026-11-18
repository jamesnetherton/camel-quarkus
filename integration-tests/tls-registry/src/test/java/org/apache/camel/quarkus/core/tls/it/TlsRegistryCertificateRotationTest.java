/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.core.tls.it;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import org.apache.camel.quarkus.test.support.certificate.TestCertificates;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.PASSWORD;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.call;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.certificateUpdates;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.keyStorePath;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.rotate;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.serial;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.starts;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rotates the client certificate of a TLS configuration used by camel-http, which uses the live key managers, and by
 * camel-vertx-http, which builds its own from a copy, and checks which certificate each presents to the
 * {@link MutualTlsServer}.
 */
@TestCertificates(certificates = {
        @Certificate(name = "tls-mtls", formats = { Format.PKCS12, Format.PEM }, password = PASSWORD, client = true),
        @Certificate(name = TlsRegistryCertificateRotationTest.ROTATING, formats = Format.PKCS12, password = PASSWORD),
})
@QuarkusTest
@TestProfile(TlsRegistryCertificateRotationTest.RotationProfile.class)
class TlsRegistryCertificateRotationTest {
    static final String ROTATING = "tls-rotating";

    @Test
    void rotatedCertificateIsUsedAndOnlyCopyingRoutesRestart() throws Exception {
        RestAssured.post("/tls-registry/reload/route/rotating-suspended/suspend").then().statusCode(204);
        RestAssured.post("/tls-registry/reload/route/rotating-stopped/stop").then().statusCode(204);

        String initialSerial = serial(ROTATING);
        assertEquals(initialSerial, call("rotating-http"));
        assertEquals(initialSerial, call("rotating-vertx-http"));
        String staticSerial = call("static-vertx-http");
        assertEquals(serial("tls-mtls-client"), staticSerial);

        assertEquals(initialSerial, call("rotating-intercepted"));
        assertEquals(initialSerial, call("rotating-aliased"));

        List<String> routeIds = List.of("rotating-http", "rotating-vertx-http", "rotating-intercepted", "rotating-aliased",
                "rotating-suspended", "rotating-stopped", "static-vertx-http", "no-tls");
        Map<String, Integer> startsBefore = new LinkedHashMap<>();
        routeIds.forEach(routeId -> startsBefore.put(routeId, starts(routeId)));

        System.gc();
        Thread.sleep(200);
        String rotatedSerial = rotate(ROTATING);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            for (String routeId : List.of("rotating-http", "rotating-vertx-http", "rotating-intercepted",
                    "rotating-aliased")) {
                assertEquals(rotatedSerial, call(routeId), routeId);
            }
            assertEquals("Suspended", status("rotating-suspended"));
        });

        // camel-http presents the new certificate through the live key managers, without a restart
        assertEquals(startsBefore.get("rotating-http"), starts("rotating-http"));
        // camel-vertx-http holds a copy, so its routes restart and keep their state
        assertEquals(startsBefore.get("rotating-vertx-http") + 1, starts("rotating-vertx-http"));
        assertEquals("Started", status("rotating-vertx-http"));
        // Also behind an interceptor, and when the component is registered under another name
        assertEquals(startsBefore.get("rotating-intercepted") + 1, starts("rotating-intercepted"));
        assertEquals(startsBefore.get("rotating-aliased") + 1, starts("rotating-aliased"));
        assertEquals(startsBefore.get("rotating-suspended") + 1, starts("rotating-suspended"));
        assertEquals("Suspended", status("rotating-suspended"));
        assertEquals(startsBefore.get("rotating-stopped"), starts("rotating-stopped"));
        assertEquals("Stopped", status("rotating-stopped"));
        // Unrelated routes do not restart
        assertEquals(startsBefore.get("static-vertx-http"), starts("static-vertx-http"));
        assertEquals(startsBefore.get("no-tls"), starts("no-tls"));
        assertEquals(staticSerial, call("static-vertx-http"));

        // Every reload period fires an event, also when no file changed. None of them restarts a route. The delay is
        // shorter than the period, so a restart scheduled by one event would run before the next
        Map<String, Integer> startsAfter = new LinkedHashMap<>();
        routeIds.forEach(routeId -> startsAfter.put(routeId, starts(routeId)));
        int updates = certificateUpdates("client-rotating");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> certificateUpdates("client-rotating") >= updates + 3);
        for (String routeId : routeIds) {
            assertEquals(startsAfter.get(routeId), starts(routeId), routeId);
        }
        assertEquals(rotatedSerial, call("rotating-vertx-http"));
    }

    public static class RotationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("quarkus.camel.tls-registry.enabled", "true"),
                    Map.entry("quarkus.camel.tls-registry.reload-certificate-delay", "500"),
                    // Requires a client certificate, and accepts any, so that a regenerated one is accepted
                    Map.entry("quarkus.tls.mtls-server.key-store.p12.path", keyStorePath("tls-mtls")),
                    Map.entry("quarkus.tls.mtls-server.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.mtls-server.trust-all", "true"),
                    // Rotated by the test
                    Map.entry("quarkus.tls.client-rotating.key-store.p12.path", keyStorePath(ROTATING)),
                    Map.entry("quarkus.tls.client-rotating.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-rotating.trust-store.p12.path",
                            "target/certs/tls-mtls-client-truststore.p12"),
                    Map.entry("quarkus.tls.client-rotating.trust-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-rotating.reload-period", "1s"),
                    // Reloaded, but never changed
                    Map.entry("quarkus.tls.client-static.key-store.p12.path", keyStorePath("tls-mtls-client")),
                    Map.entry("quarkus.tls.client-static.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-static.trust-store.p12.path",
                            "target/certs/tls-mtls-client-truststore.p12"),
                    Map.entry("quarkus.tls.client-static.trust-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-static.reload-period", "1s"),
                    Map.entry("tls-registry.reload-routes", String.join(",",
                            "rotating-http=https:client-rotating",
                            "rotating-vertx-http=vertx-http:client-rotating",
                            "rotating-intercepted=vertx-http-intercepted:client-rotating",
                            "rotating-aliased=vertx-http-aliased:client-rotating",
                            "rotating-suspended=vertx-http:client-rotating",
                            "rotating-stopped=vertx-http:client-rotating",
                            "static-vertx-http=vertx-http:client-static",
                            "no-tls=none")));
        }
    }
}
