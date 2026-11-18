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
package org.apache.camel.quarkus.core.tls;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsMaterialFingerprintTest {
    private static final String ALIAS = "test";
    private static final char[] PASSWORD = "changeit".toCharArray();

    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBTzCB9qADAgECAgkArfQl3nvPsEUwCgYIKoZIzj0EAwMwGzEZMBcGA1UEAxMQ
            ZmluZ2VycHJpbnQtdGVzdDAgFw0yNjExMTgwOTA4MThaGA8yMTI2MTAyNTA5MDgx
            OFowGzEZMBcGA1UEAxMQZmluZ2VycHJpbnQtdGVzdDBZMBMGByqGSM49AgEGCCqG
            SM49AwEHA0IABPc+h3mMnDe1NZkGaf3NFASSisQzJ9qET9W3LZLxzWZQ5Yy36ej8
            jhnaDBaudv/CbueHlODijYBbNXREtwYD0B+jITAfMB0GA1UdDgQWBBTI/ca8QLoy
            4PA+G/7Ht1Za+Rv7dTAKBggqhkjOPQQDAwNIADBFAiAwm+HruaKQPyZQUv3oQLD4
            ntb4o9kV5FbQp7x1twcr+AIhALh3fq53NwyPuy3RM+czPiPzvIOeob1wEx7h7JrR
            3Z8/
            -----END CERTIFICATE-----
            """;

    @Test
    void sameCertificateWithDifferentKeyChangesFingerprint() throws Exception {
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(CERTIFICATE.getBytes(StandardCharsets.US_ASCII)));
        PrivateKey key = newKey();
        PrivateKey otherKey = newKey();

        String fingerprint = fingerprint(certificate, key);

        assertEquals(fingerprint, fingerprint(certificate, key));
        assertNotEquals(fingerprint, fingerprint(certificate, otherKey));
    }

    @Test
    void trustFingerprintIgnoresIssuerOrder() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        X509Certificate[] issuers = null;
        for (TrustManager candidate : factory.getTrustManagers()) {
            if (candidate instanceof X509TrustManager x509TrustManager) {
                issuers = x509TrustManager.getAcceptedIssuers();
            }
        }
        assertTrue(issuers.length > 1);

        List<X509Certificate> reversed = new ArrayList<>(List.of(issuers));
        Collections.reverse(reversed);
        String fingerprint = TlsMaterial.trustFingerprint(trustManager(issuers));

        assertEquals(fingerprint, TlsMaterial.trustFingerprint(trustManager(reversed.toArray(new X509Certificate[0]))));
        assertNotEquals(fingerprint,
                TlsMaterial.trustFingerprint(trustManager(Arrays.copyOf(issuers, issuers.length - 1))));
    }

    private static X509TrustManager trustManager(X509Certificate[] issuers) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return issuers;
            }
        };
    }

    private static String fingerprint(Certificate certificate, PrivateKey key) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(ALIAS, key, PASSWORD, new Certificate[] { certificate });

        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore, PASSWORD);
        X509KeyManager keyManager = null;
        for (KeyManager candidate : factory.getKeyManagers()) {
            if (candidate instanceof X509KeyManager x509KeyManager) {
                keyManager = x509KeyManager;
            }
        }

        List<String> aliases = List.of(keyManager.getClientAliases("EC", null));
        return TlsMaterial.keyFingerprint(keyManager, aliases);
    }

    private static PrivateKey newKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair().getPrivate();
    }
}
