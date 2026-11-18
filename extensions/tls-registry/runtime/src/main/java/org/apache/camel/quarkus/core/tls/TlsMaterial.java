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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TrustOptions;

/**
 * The key and trust material of a Quarkus TLS configuration at one point in time.
 *
 * Every value is derived from a single generation of the configuration, so that the fingerprints always describe the
 * material that the key and trust managers serve.
 */
final class TlsMaterial {
    private static final int MAX_READ_ATTEMPTS = 10;

    private final X509KeyManager keyManager;
    private final Set<String> keyAliases;
    private final X509TrustManager trustManager;
    private final KeyStore trustStore;
    private final String keyFingerprint;
    private final String trustFingerprint;

    TlsMaterial(X509KeyManager keyManager, Set<String> keyAliases, X509TrustManager trustManager, KeyStore trustStore) {
        this.keyManager = keyManager;
        this.keyAliases = keyAliases;
        this.trustManager = trustManager;
        this.trustStore = trustStore;
        this.keyFingerprint = keyFingerprint(keyManager, keyAliases);
        this.trustFingerprint = trustFingerprint(trustManager);
    }

    /**
     * Read the current material of a Quarkus TLS configuration.
     *
     * A reload replaces the options and key stores of the configuration together, but each getter locks separately.
     * The options are therefore read again after the key stores, and the read is retried if a reload happened in
     * between.
     */
    static TlsMaterial load(TlsConfiguration tlsConfig, Vertx vertx) throws GeneralSecurityException {
        for (int attempt = 0; attempt < MAX_READ_ATTEMPTS; attempt++) {
            KeyCertOptions keyStoreOptions = tlsConfig.getKeyStoreOptions();
            TrustOptions trustOptions = tlsConfig.getTrustStoreOptions();
            KeyStore keyStore = tlsConfig.getKeyStore();
            KeyStore trustStore = tlsConfig.getTrustStore();
            if (keyStoreOptions == tlsConfig.getKeyStoreOptions() && trustOptions == tlsConfig.getTrustStoreOptions()) {
                return load(keyStoreOptions, keyStore, trustOptions, trustStore, vertx);
            }
        }
        throw new GeneralSecurityException("The TLS configuration changed during every read attempt");
    }

    private static TlsMaterial load(
            KeyCertOptions keyStoreOptions,
            KeyStore keyStore,
            TrustOptions trustOptions,
            KeyStore trustStore,
            Vertx vertx) throws GeneralSecurityException {

        X509KeyManager keyManager = null;
        Set<String> keyAliases = Set.of();
        if (keyStoreOptions != null && keyStore != null) {
            try {
                keyManager = x509KeyManager(keyStoreOptions.getKeyManagerFactory(vertx));
            } catch (GeneralSecurityException e) {
                throw e;
            } catch (Exception e) {
                throw new GeneralSecurityException("Failed to create the key manager", e);
            }
            keyAliases = keyAliases(keyManager, keyStore);
        }

        X509TrustManager trustManager;
        try {
            trustManager = trustOptions != null
                    ? x509TrustManager(trustOptions.getTrustManagerFactory(vertx))
                    : defaultTrustManager();
        } catch (GeneralSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to create the trust manager", e);
        }

        return new TlsMaterial(keyManager, keyAliases, trustManager, trustStore);
    }

    /**
     * The key manager, or null when the configuration has no key store.
     */
    X509KeyManager keyManager() {
        return keyManager;
    }

    /**
     * The aliases under which {@link #keyManager()} serves a private key.
     */
    Set<String> keyAliases() {
        return keyAliases;
    }

    /**
     * The trust manager. The JVM default trust manager when the configuration has no trust store.
     */
    X509TrustManager trustManager() {
        return trustManager;
    }

    /**
     * The trust store loaded by Quarkus, or null when the configuration has none.
     */
    KeyStore trustStore() {
        return trustStore;
    }

    /**
     * Identifies the key material: SHA-256 over the certificate chain and private key of every key entry.
     */
    String keyFingerprint() {
        return keyFingerprint;
    }

    /**
     * Identifies the trust material: SHA-256 over the accepted issuers of the trust manager.
     */
    String trustFingerprint() {
        return trustFingerprint;
    }

    /**
     * SHA-256 over the certificate chain and private key of every key entry. The private key is part of it because a
     * PEM certificate and its key are separate files, which can be read between two writes. A key that cannot be
     * encoded, such as a hardware key, contributes nothing.
     */
    static String keyFingerprint(X509KeyManager keyManager, Collection<String> keyAliases) {
        try {
            // Hash each entry on its own and sort the results, so that the alias names and order do not matter
            List<String> entries = new ArrayList<>();
            if (keyManager != null) {
                for (String alias : keyAliases) {
                    MessageDigest entry = MessageDigest.getInstance("SHA-256");
                    X509Certificate[] chain = keyManager.getCertificateChain(alias);
                    if (chain != null) {
                        for (X509Certificate certificate : chain) {
                            update(entry, certificate.getEncoded());
                        }
                    }
                    PrivateKey key = keyManager.getPrivateKey(alias);
                    if (key != null && key.getEncoded() != null) {
                        update(entry, key.getEncoded());
                    }
                    entries.add(HexFormat.of().formatHex(entry.digest()));
                }
            }
            Collections.sort(entries);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String entry : entries) {
                update(digest, entry.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute the TLS key material fingerprint", e);
        }
    }

    /**
     * SHA-256 over the accepted issuers of the trust manager.
     */
    static String trustFingerprint(X509TrustManager trustManager) {
        try {
            // Hash each issuer on its own and sort the results, so that the order they are returned in does not matter
            List<String> issuers = new ArrayList<>();
            if (trustManager != null) {
                for (X509Certificate issuer : trustManager.getAcceptedIssuers()) {
                    issuers.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(issuer.getEncoded())));
                }
            }
            Collections.sort(issuers);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String issuer : issuers) {
                update(digest, issuer.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute the TLS trust material fingerprint", e);
        }
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        // Length prefixed, so that adjacent values cannot be confused
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    /**
     * The key manager does not list its aliases directly, only the ones matching a key type. It may also name them
     * differently from the key store, depending on the KeyManagerFactory algorithm.
     */
    private static Set<String> keyAliases(X509KeyManager keyManager, KeyStore keyStore) throws GeneralSecurityException {
        Set<String> keyTypes = new LinkedHashSet<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            Certificate certificate = keyStore.getCertificate(alias);
            if (keyStore.isKeyEntry(alias) && certificate != null) {
                keyTypes.add(certificate.getPublicKey().getAlgorithm());
            }
        }

        Set<String> aliases = new LinkedHashSet<>();
        for (String keyType : keyTypes) {
            addAll(aliases, keyManager.getServerAliases(keyType, null));
            addAll(aliases, keyManager.getClientAliases(keyType, null));
        }

        if (!keyTypes.isEmpty() && aliases.isEmpty()) {
            // Fail rather than serve no key, which would silently connect without a client certificate
            throw new GeneralSecurityException("No private key could be read from the key store");
        }
        return Collections.unmodifiableSet(aliases);
    }

    private static void addAll(Set<String> target, String[] values) {
        if (values != null) {
            Collections.addAll(target, values);
        }
    }

    private static X509KeyManager x509KeyManager(KeyManagerFactory keyManagerFactory) throws GeneralSecurityException {
        if (keyManagerFactory != null) {
            for (KeyManager keyManager : keyManagerFactory.getKeyManagers()) {
                if (keyManager instanceof X509KeyManager x509KeyManager) {
                    return x509KeyManager;
                }
            }
        }
        throw new GeneralSecurityException("No X509KeyManager available for the key store");
    }

    private static X509TrustManager x509TrustManager(TrustManagerFactory trustManagerFactory)
            throws GeneralSecurityException {
        if (trustManagerFactory != null) {
            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager x509TrustManager) {
                    return x509TrustManager;
                }
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available for the trust store");
    }

    private static X509TrustManager defaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        return x509TrustManager(factory);
    }
}
