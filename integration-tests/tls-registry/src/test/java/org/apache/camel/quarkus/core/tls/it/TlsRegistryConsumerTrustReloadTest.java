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

import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.CERTS_DIR;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.PASSWORD;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.rotateServerTrustStore;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.starts;
import static org.apache.camel.quarkus.core.tls.it.CertificateRotation.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A camel-netty consumer only uses the live trust manager of its bean. When the client CA is removed from its trust
 * store, a client that connected before must not be able to resume its session.
 */
@TestCertificates(replaceIfExists = true, certificates = {
        @Certificate(name = TlsRegistryConsumerTrustReloadTest.CERTIFICATE, formats = Format.PKCS12, password = PASSWORD, client = true),
})
@QuarkusTest
@TestProfile(TlsRegistryConsumerTrustReloadTest.ConsumerTrustReloadProfile.class)
class TlsRegistryConsumerTrustReloadTest {
    static final String CERTIFICATE = "tls-consumer";
    private static final String CLIENT_CALL = "/tls-registry/reload/consumer/consumer-client/call";

    @Test
    void clientNoLongerTrustedCannotResumeItsSession() throws Exception {
        // The client keeps its SSLContext, so the second call can resume the session of the first
        RestAssured.get(CLIENT_CALL).then().statusCode(200).body(equalTo("ok"));
        RestAssured.get(CLIENT_CALL).then().statusCode(200).body(equalTo("ok"));
        int startsBefore = starts("consumer");

        rotateServerTrustStore(CERTIFICATE);

        // Until the consumer restarts, the server accepts the session ticket of the client without checking its
        // certificate again
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(startsBefore + 1, starts("consumer"));
            assertEquals("Started", status("consumer"));
        });

        // The client tries to resume, the new SSLContext cannot read its ticket, and the full handshake rejects it
        RestAssured.get(CLIENT_CALL).then().statusCode(500).body(not(containsString("Connection refused")));
    }

    public static class ConsumerTrustReloadProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            String prefix = CERTS_DIR + "/" + CERTIFICATE;
            return Map.ofEntries(
                    Map.entry("quarkus.camel.tls-registry.enabled", "true"),
                    Map.entry("quarkus.camel.tls-registry.reload-certificate-delay", "500"),
                    Map.entry("quarkus.tls.mtls-consumer.key-store.p12.path", prefix + "-keystore.p12"),
                    Map.entry("quarkus.tls.mtls-consumer.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.mtls-consumer.trust-store.p12.path", prefix + "-server-truststore.p12"),
                    Map.entry("quarkus.tls.mtls-consumer.trust-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.mtls-consumer.reload-period", "1s"),
                    Map.entry("quarkus.tls.consumer-client.key-store.p12.path", prefix + "-client-keystore.p12"),
                    Map.entry("quarkus.tls.consumer-client.key-store.p12.password", PASSWORD),
                    Map.entry("quarkus.tls.consumer-client.trust-store.p12.path", prefix + "-client-truststore.p12"),
                    Map.entry("quarkus.tls.consumer-client.trust-store.p12.password", PASSWORD),
                    Map.entry("tls-registry.reload-routes", "consumer=netty-consumer:mtls-consumer"));
        }
    }
}
