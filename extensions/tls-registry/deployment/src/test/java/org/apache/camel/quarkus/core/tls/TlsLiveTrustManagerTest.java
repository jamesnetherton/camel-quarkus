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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.camel.quarkus.core.tls.TlsLiveMaterial.LiveTrustManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsLiveTrustManagerTest {
    private static final X509Certificate[] CHAIN = new X509Certificate[0];

    @Test
    void delegateWithoutEndpointIdentificationIsRejected() {
        LiveTrustManager trustManager = new LiveTrustManager();
        trustManager.delegate = new AcceptingExtendedTrustManager();

        assertDoesNotThrow(() -> trustManager.checkServerTrusted(CHAIN, "RSA", (SSLEngine) null));

        // A reload replaces the delegate with one that cannot check the peer host name
        trustManager.delegate = new AcceptingTrustManager();

        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(CHAIN, "RSA", (SSLEngine) null));
        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(CHAIN, "RSA", (Socket) null));
        assertThrows(CertificateException.class, () -> trustManager.checkClientTrusted(CHAIN, "RSA", (SSLEngine) null));
        assertThrows(CertificateException.class, () -> trustManager.checkClientTrusted(CHAIN, "RSA", (Socket) null));

        // The view handed to the JVM for such a delegate still works, the JVM adds endpoint identification to it
        X509TrustManager basic = trustManager.basic();
        assertDoesNotThrow(() -> basic.checkServerTrusted(CHAIN, "RSA"));
        assertDoesNotThrow(() -> basic.checkClientTrusted(CHAIN, "RSA"));
    }

    private static class AcceptingTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static final class AcceptingExtendedTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }
    }
}
