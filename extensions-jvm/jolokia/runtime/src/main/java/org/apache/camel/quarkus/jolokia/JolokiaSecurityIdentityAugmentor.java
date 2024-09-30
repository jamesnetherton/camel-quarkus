package org.apache.camel.quarkus.jolokia;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.security.credential.CertificateCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class JolokiaSecurityIdentityAugmentor implements SecurityIdentityAugmentor {
    @ConfigProperty(name = "quarkus.camel.jolokia.kubernetes.client-principal")
    String clientPrincipal;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return Uni.createFrom().item(build(identity));
    }

    private Supplier<SecurityIdentity> build(SecurityIdentity identity) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        CertificateCredential certificate = identity.getCredential(CertificateCredential.class);
        if (certificate != null) {
            builder.addRoles(extractRoles(certificate.getCertificate()));
        }
        return builder::build;
    }

    private Set<String> extractRoles(X509Certificate certificate) {
        String name = certificate.getSubjectX500Principal().getName();
        System.out.println(clientPrincipal);
        if (name.equals(clientPrincipal)) {
            return Collections.singleton("jolokia");
        }
        return Collections.emptySet();
    }
}
