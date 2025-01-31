package org.apache.camel.quarkus.jolokia;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.security.credential.CertificateCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

public class JolokiaSecurityIdentityAugmentor implements SecurityIdentityAugmentor {
    @Inject
    JolokiaRuntimeConfig config;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        System.out.println(identity);
        return Uni.createFrom().item(build(identity));
    }

    private Supplier<SecurityIdentity> build(SecurityIdentity identity) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        CertificateCredential certificate = identity.getCredential(CertificateCredential.class);
        System.out.println(certificate);
        if (certificate != null) {
            builder.addRoles(extractRoles(certificate.getCertificate()));
        }
        return builder::build;
    }

    private Set<String> extractRoles(X509Certificate certificate) {
        String name = certificate.getSubjectX500Principal().getName();
        System.out.println(name);
        Optional<String> clientPrincipal = config.kubernetes().clientPrincipal();
        System.out.println(clientPrincipal.orElse(null));
        if (clientPrincipal.isPresent() && name.equals(clientPrincipal.get())) {
            return Collections.singleton("jolokia");
        }
        return Collections.emptySet();
    }
}
