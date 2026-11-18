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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The current key and trust material of a Quarkus TLS configuration, served through key and trust managers that
 * follow a certificate reload.
 */
final class TlsLiveMaterial {
    // The KeyStore API needs a password to hold a private key, and components need it to read the key back, so the
    // key store copy is handed out together with it. At least 32 characters for FIPS providers, as Vert.x does
    static final String KEY_STORE_COPY_PASSWORD = "camel-quarkus-tls-registry-in-memory-key-store";
    // How long after a change the refreshes keep sweeping for sessions established with the previous material
    private static final long LATE_SESSION_MILLIS = 60_000;
    private static final Logger LOG = LoggerFactory.getLogger(TlsLiveMaterial.class);

    private final String name;
    private final TlsConfiguration tlsConfig;
    private final Vertx vertx;
    private final GenerationalKeyManager keyManager = new GenerationalKeyManager();
    private final LiveTrustManager trustManager = new LiveTrustManager();
    private final Set<SSLContext> contexts = Collections.newSetFromMap(new WeakHashMap<>());

    private volatile TlsMaterial current;
    private long lastSwapMillis;
    private boolean sweepPending;
    private volatile boolean materialHandedOut;
    private KeyStore keyStoreCopy;
    private String keyStoreCopyFingerprint;

    TlsLiveMaterial(String name, TlsConfiguration tlsConfig, Vertx vertx) {
        this.name = name;
        this.tlsConfig = tlsConfig;
        this.vertx = vertx;
        try {
            swap(TlsMaterial.load(tlsConfig, vertx));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to read the key material of Quarkus TLS configuration '" + name + "'", e);
        }
    }

    String name() {
        return name;
    }

    TlsConfiguration tlsConfiguration() {
        return tlsConfig;
    }

    TlsMaterial current() {
        return current;
    }

    /**
     * What a refresh found changed.
     *
     * @param keys  whether the key material changed
     * @param trust whether the trust material changed
     */
    record Changes(boolean keys, boolean trust) {
        static final Changes NONE = new Changes(false, false);

        boolean any() {
            return keys || trust;
        }
    }

    /**
     * Read the material of the TLS configuration again, and switch to it when it differs from the current one.
     *
     * The material is read under the lock, so that concurrent refreshes cannot replace newer material with older.
     *
     * @return what changed
     */
    Changes refresh() throws GeneralSecurityException {
        Changes changes;
        long sweep;
        synchronized (this) {
            TlsMaterial next = TlsMaterial.load(tlsConfig, vertx);
            changes = new Changes(
                    !next.keyFingerprint().equals(current.keyFingerprint()),
                    !next.trustFingerprint().equals(current.trustFingerprint()));
            if (changes.any()) {
                swap(next);
                lastSwapMillis = System.currentTimeMillis();
                sweepPending = true;
            }
            sweep = sweepPending ? lastSwapMillis : -1;
            if (!changes.any() && System.currentTimeMillis() - lastSwapMillis > LATE_SESSION_MILLIS) {
                sweepPending = false;
            }
        }
        if (sweep >= 0) {
            // A connection that started with the previous material can cache its session after the sweep made when
            // the material changed, since a session ticket arrives after the handshake. Such a session keeps the
            // creation time of its handshake, so the refreshes that follow a change sweep again
            int invalidated = invalidateSessions(sweep);
            if (invalidated > 0 && !changes.any()) {
                LOG.debug("Invalidated {} session(s) of TLS configuration '{}' established with its previous material",
                        invalidated, name);
            }
        }
        return changes.any() ? changes : Changes.NONE;
    }

    private synchronized void swap(TlsMaterial next) {
        keyManager.swap(next.keyManager());
        trustManager.delegate = next.trustManager();
        current = next;
    }

    /**
     * Create an SSLContext that serves the current material, and the material of any later reload.
     */
    SSLContext createSSLContext() throws GeneralSecurityException {
        TlsMaterial material = current;
        KeyManager[] keyManagers = material.keyManager() != null ? new KeyManager[] { keyManager } : null;
        TrustManager trust = material.trustManager() instanceof X509ExtendedTrustManager
                ? trustManager
                : trustManager.basic();

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, new TrustManager[] { trust }, new SecureRandom());
        synchronized (contexts) {
            contexts.add(context);
        }
        return context;
    }

    /**
     * An in-memory copy of the private keys and certificate chains, protected by {@link #KEY_STORE_COPY_PASSWORD}.
     * Copied again after the material changes.
     *
     * Components that build their own KeyManagerFactory need the key password alongside the key store. The password
     * of the key store loaded by Quarkus is not available to Camel: it is the configured password, an alias password,
     * or for PEM an internal Vert.x one. Reading the keys through the key manager that Vert.x builds avoids needing it,
     * and keeps the alias selection Vert.x applies when {@code key-store.*.alias} is set.
     *
     * @return the key store, or null when the configuration has no key store
     */
    synchronized KeyStore keyStoreCopy() {
        TlsMaterial material = current;
        X509KeyManager source = material.keyManager();
        if (source == null) {
            return null;
        }
        if (!material.keyFingerprint().equals(keyStoreCopyFingerprint)) {
            try {
                KeyStore copy = KeyStore.getInstance(KeyStore.getDefaultType());
                copy.load(null, null);
                for (String alias : material.keyAliases()) {
                    PrivateKey key = source.getPrivateKey(alias);
                    X509Certificate[] chain = source.getCertificateChain(alias);
                    if (key != null && chain != null && chain.length > 0) {
                        copy.setKeyEntry(alias, key, KEY_STORE_COPY_PASSWORD.toCharArray(), chain);
                    }
                }
                keyStoreCopy = copy;
                keyStoreCopyFingerprint = material.keyFingerprint();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to copy the key material of Quarkus TLS configuration '" + name + "'", e);
            }
        }
        materialHandedOut = true;
        return keyStoreCopy;
    }

    /**
     * The trust store of the current material.
     *
     * @return the trust store, or null when the configuration has none
     */
    KeyStore trustStore() {
        KeyStore trustStore = current.trustStore();
        if (trustStore != null) {
            materialHandedOut = true;
        }
        return trustStore;
    }

    /**
     * Whether key or trust material was ever handed out as a copy, which does not follow a reload.
     */
    boolean materialHandedOut() {
        return materialHandedOut;
    }

    /**
     * Remove the sessions cached by the contexts handed out that were created up to the given time, so that a new
     * connection does not resume a session established with the previous material.
     *
     * @return the number of sessions invalidated
     */
    private int invalidateSessions(long createdUpToMillis) {
        List<SSLContext> snapshot;
        synchronized (contexts) {
            snapshot = new ArrayList<>(contexts);
        }
        int invalidated = 0;
        for (SSLContext context : snapshot) {
            invalidated += invalidate(context.getClientSessionContext(), createdUpToMillis);
            invalidated += invalidate(context.getServerSessionContext(), createdUpToMillis);
        }
        return invalidated;
    }

    private static int invalidate(SSLSessionContext sessionContext, long createdUpToMillis) {
        if (sessionContext == null) {
            return 0;
        }
        int invalidated = 0;
        for (Enumeration<byte[]> ids = sessionContext.getIds(); ids.hasMoreElements();) {
            SSLSession session = sessionContext.getSession(ids.nextElement());
            if (session != null && session.getCreationTime() <= createdUpToMillis) {
                session.invalidate();
                invalidated++;
            }
        }
        return invalidated;
    }

    /**
     * Delegates to the key manager of the current material. The aliases it returns carry the generation of the key
     * manager that chose them, so that the private key and certificate chain of an alias come from that key manager,
     * also when a reload happens during the handshake.
     */
    static final class GenerationalKeyManager extends X509ExtendedKeyManager {
        private static final char SEPARATOR = ':';

        private record Generation(long id, X509KeyManager keyManager) {
        }

        private volatile Generation current;
        private volatile Generation previous;

        synchronized void swap(X509KeyManager keyManager) {
            long id = current == null ? 0 : current.id() + 1;
            previous = current;
            current = keyManager != null ? new Generation(id, keyManager) : null;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            Generation generation = current;
            return generation == null ? null
                    : tag(generation, generation.keyManager().chooseClientAlias(keyType, issuers, socket));
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            Generation generation = current;
            return generation == null ? null
                    : tag(generation, generation.keyManager().chooseServerAlias(keyType, issuers, socket));
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            Generation generation = current;
            if (generation == null) {
                return null;
            }
            X509KeyManager delegate = generation.keyManager();
            String alias = delegate instanceof X509ExtendedKeyManager extended
                    ? extended.chooseEngineClientAlias(keyType, issuers, engine)
                    : delegate.chooseClientAlias(keyType, issuers, null);
            return tag(generation, alias);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            Generation generation = current;
            if (generation == null) {
                return null;
            }
            X509KeyManager delegate = generation.keyManager();
            String alias = delegate instanceof X509ExtendedKeyManager extended
                    ? extended.chooseEngineServerAlias(keyType, issuers, engine)
                    : delegate.chooseServerAlias(keyType, issuers, null);
            return tag(generation, alias);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            Generation generation = current;
            return generation == null ? null : tag(generation, generation.keyManager().getClientAliases(keyType, issuers));
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            Generation generation = current;
            return generation == null ? null : tag(generation, generation.keyManager().getServerAliases(keyType, issuers));
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            Generation generation = generation(alias);
            return generation == null ? null : generation.keyManager().getCertificateChain(untag(alias));
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            Generation generation = generation(alias);
            return generation == null ? null : generation.keyManager().getPrivateKey(untag(alias));
        }

        private static String tag(Generation generation, String alias) {
            return alias == null ? null : generation.id() + String.valueOf(SEPARATOR) + alias;
        }

        private static String[] tag(Generation generation, String[] aliases) {
            if (aliases == null) {
                return null;
            }
            String[] tagged = new String[aliases.length];
            for (int i = 0; i < aliases.length; i++) {
                tagged[i] = tag(generation, aliases[i]);
            }
            return tagged;
        }

        private static String untag(String alias) {
            return alias.substring(alias.indexOf(SEPARATOR) + 1);
        }

        private Generation generation(String alias) {
            if (alias == null) {
                return null;
            }
            int separator = alias.indexOf(SEPARATOR);
            if (separator < 1) {
                return null;
            }
            long id;
            try {
                id = Long.parseLong(alias, 0, separator, 10);
            } catch (NumberFormatException e) {
                return null;
            }
            Generation generation = current;
            if (generation != null && generation.id() == id) {
                return generation;
            }
            generation = previous;
            return generation != null && generation.id() == id ? generation : null;
        }
    }

    /**
     * Delegates to the trust manager of the current material.
     */
    static final class LiveTrustManager extends X509ExtendedTrustManager {
        volatile X509TrustManager delegate;

        /**
         * A view that only implements {@link X509TrustManager}, for a delegate that does not extend
         * {@link X509ExtendedTrustManager}. The JVM then adds endpoint identification, as it does for the delegate.
         */
        X509TrustManager basic() {
            return new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    LiveTrustManager.this.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    LiveTrustManager.this.checkServerTrusted(chain, authType);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return LiveTrustManager.this.getAcceptedIssuers();
                }
            };
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        /**
         * The delegate, for a check that carries the socket or engine. An SSLContext only calls these when it was given
         * this trust manager directly, in which case the JVM leaves endpoint identification to it. A delegate that
         * cannot do it is rejected rather than used without.
         */
        private X509ExtendedTrustManager extended() throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager extended) {
                return extended;
            }
            throw new CertificateException(
                    "The trust manager of the reloaded TLS configuration cannot verify the peer of this connection");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            extended().checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            extended().checkServerTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            extended().checkClientTrusted(chain, authType, engine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            extended().checkServerTrusted(chain, authType, engine);
        }
    }
}
