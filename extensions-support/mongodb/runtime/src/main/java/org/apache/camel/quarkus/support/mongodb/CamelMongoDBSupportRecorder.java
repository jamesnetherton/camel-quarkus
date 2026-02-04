package org.apache.camel.quarkus.support.mongodb;

import java.lang.annotation.Annotation;

import com.mongodb.client.MongoClient;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.mongodb.MongoClientName;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import jakarta.enterprise.inject.Default;
import org.apache.camel.quarkus.core.CamelBeanQualifierResolver;

@Recorder
public class CamelMongoDBSupportRecorder {
    public RuntimeValue<CamelBeanQualifierResolver> createMongoClientNameQualifierResolver(String mongoClientName) {
        return new RuntimeValue<>(new CamelBeanQualifierResolver() {
            final MongoClientName.Literal mongoClientNameLiteral = new MongoClientName.Literal(mongoClientName);

            @Override
            public Annotation[] resolveQualifiers() {
                return new Annotation[] { mongoClientNameLiteral };
            }
        });
    }

    public RuntimeValue<?> getDefaultMongoClient() {
        InjectableInstance<MongoClient> defaultMongoClient = Arc.container()
                .select(MongoClient.class, Default.Literal.INSTANCE);
        if (defaultMongoClient.isResolvable()) {
            return new RuntimeValue<>(defaultMongoClient.get());
        }
        return null;
    }
}
