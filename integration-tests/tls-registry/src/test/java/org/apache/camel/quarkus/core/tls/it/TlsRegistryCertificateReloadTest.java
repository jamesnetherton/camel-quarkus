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
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.keyStorePath;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.lastStarted;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.rotate;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.serial;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.starts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestCertificates(certificates = {
        @Certificate(name = "tls-mtls", formats = { Format.PKCS12, Format.PEM }, password = PASSWORD, client = true),
        @Certificate(name = TlsRegistryCertificateReloadTest.ROTATING_A, formats = Format.PKCS12, password = PASSWORD),
        @Certificate(name = TlsRegistryCertificateReloadTest.ROTATING_B, formats = Format.PKCS12, password = PASSWORD),
})
@QuarkusTest
@TestProfile(TlsRegistryCertificateReloadTest.CertReloadTestProfile.class)
class TlsRegistryCertificateReloadTest {
    static final String ROTATING_A = "tls-rotating-a";
    static final String ROTATING_B = "tls-rotating-b";
    private static final long RELOAD_DELAY_MILLIS = 3000;

    @Test
    void updateEventWithoutChangedCertificateRestartsNothing() throws Exception {
        // camel-vertx-http copies the key material, so a real change would restart the route
        assertEquals(serial(ROTATING_A), call("route-a"));
        int startsA = starts("route-a");
        int startsB = starts("route-b");

        RestAssured.post("/tls-registry/reload/fire-event/client-a").then().statusCode(200);

        // Had the event scheduled a restart for A, A would restart together with B, or before it
        String serialB = rotate(ROTATING_B);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(serialB, call("route-b"));
            assertEquals(startsB + 1, starts("route-b"));
        });
        assertEquals(startsA, starts("route-a"));
    }

    @Test
    void changesWithinTheDelayRestartEachRouteOnce() throws Exception {
        assertEquals(serial(ROTATING_A), call("route-a"));
        assertEquals(serial(ROTATING_B), call("route-b"));
        int startsA = starts("route-a");
        int startsB = starts("route-b");
        int startsNoTls = starts("no-tls");

        String serialA = rotate(ROTATING_A);
        // camel-http uses the live key managers, so it presents the new certificate as soon as the change is detected.
        // B then changes within the delay of the change to A, so that the restart for A waits for B
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(serialA, call("route-a-live")));
        String serialB = rotate(ROTATING_B);
        long rotatedB = System.currentTimeMillis();

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(serialA, call("route-a"));
            assertEquals(serialB, call("route-b"));
        });

        assertEquals(startsA + 1, starts("route-a"));
        assertEquals(startsB + 1, starts("route-b"));
        assertEquals(startsNoTls, starts("no-tls"));
        assertTrue(lastStarted("route-a") >= rotatedB, "The restart for A waited for the change to B");
    }

    private static String serial(String name) {
        try {
            return CertificateRotation.serial(name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static class CertReloadTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("quarkus.camel.tls-registry.enabled", "true"),
                    Map.entry("quarkus.camel.tls-registry.reload-certificate-delay", String.valueOf(RELOAD_DELAY_MILLIS)),
                    Map.entry("quarkus.tls.mtls-server.key-store.p12.path", keyStorePath("tls-mtls")),
                    Map.entry("quarkus.tls.mtls-server.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.mtls-server.trust-all", "true"),
                    Map.entry("quarkus.tls.client-a.key-store.p12.path", keyStorePath(ROTATING_A)),
                    Map.entry("quarkus.tls.client-a.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-a.trust-all", "true"),
                    Map.entry("quarkus.tls.client-a.reload-period", "1s"),
                    Map.entry("quarkus.tls.client-b.key-store.p12.path", keyStorePath(ROTATING_B)),
                    Map.entry("quarkus.tls.client-b.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.client-b.trust-all", "true"),
                    Map.entry("quarkus.tls.client-b.reload-period", "1s"),
                    Map.entry("tls-registry.reload-routes", String.join(",",
                            "route-a=vertx-http:client-a",
                            "route-a-live=https:client-a",
                            "route-b=vertx-http:client-b",
                            "no-tls=none")));
        }
    }
}
