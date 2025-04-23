package org.apache.camel.quarkus.test;

import java.util.Collection;
import java.util.function.Consumer;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
import org.apache.camel.quarkus.core.deployment.spi.DisableCamelRuntimeAutoStartupBuildItem;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

public class CamelQuarkusTestBuildChainCustomizerProducer implements TestBuildChainCustomizerProducer {
    @Override
    public Consumer<BuildChainBuilder> produce(Index testClassesIndex) {
        Collection<ClassInfo> testSupportClasses = testClassesIndex
                .getAllKnownSubclasses(DotName.createSimple(CamelQuarkusTestSupport.class));
        if (!testSupportClasses.isEmpty()) {
            return new Consumer<BuildChainBuilder>() {
                @Override
                public void accept(BuildChainBuilder builder) {
                    builder.addBuildStep(new BuildStep() {
                        @Override
                        public void execute(BuildContext context) {
                            context.produce(new DisableCamelRuntimeAutoStartupBuildItem());
                        }
                    }).produces(DisableCamelRuntimeAutoStartupBuildItem.class)
                            .build();
                }
            };
        }

        return null;
    }
}
