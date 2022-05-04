package org.apache.camel.quarkus.test.junit5;

import java.util.function.Consumer;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
import org.jboss.jandex.Index;

public class CamelQuarkusTestBuildChainCustomizerProducer implements TestBuildChainCustomizerProducer {

    @Override
    public Consumer<BuildChainBuilder> produce(Index testClassesIndex) {
        return new Consumer<BuildChainBuilder>() {
            @Override
            public void accept(BuildChainBuilder builder) {
                builder.addBuildStep(new BuildStep() {
                    @Override
                    public void execute(BuildContext context) {
                        context.consume(AnnotationsTransformerBuildItem.class);
                    }
                });
            }
        };
    }
}
