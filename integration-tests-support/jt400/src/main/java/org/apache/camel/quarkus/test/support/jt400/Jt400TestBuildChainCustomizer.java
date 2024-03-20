package org.apache.camel.quarkus.test.support.jt400;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
import org.jboss.jandex.Index;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * TestBuildChainCustomizerProducer to modify the bytecode of various JT400 classes to add the public modifier.
 * Prevents the need to force a flat test classpath when mock tests are enabled.
 */
public class Jt400TestBuildChainCustomizer implements TestBuildChainCustomizerProducer {
    private static final List<String> NON_PUBLIC_JT400_CLASSES = List.of(
            "com.ibm.as400.access.DQReadNormalReplyDataStream",
            "com.ibm.as400.access.DQRequestAttributesNormalReplyDataStream",
            "com.ibm.as400.access.DQCommonReplyDataStream",
            "com.ibm.as400.access.RCExchangeAttributesReplyDataStream",
            "com.ibm.as400.access.RCCallProgramReplyDataStream",
            "com.ibm.as400.access.AS400NoThreadServer");

    @Override
    public Consumer<BuildChainBuilder> produce(Index testClassesIndex) {
        return new Consumer<BuildChainBuilder>() {
            @Override
            public void accept(BuildChainBuilder buildChainBuilder) {
                if (System.getProperty("skip-mock-tests") == null) {
                    buildChainBuilder.addBuildStep(new BuildStep() {
                        @Override
                        public void execute(BuildContext context) {
                            for (String className : NON_PUBLIC_JT400_CLASSES) {
                                BytecodeTransformerBuildItem buildItem = new BytecodeTransformerBuildItem(
                                        className,
                                        new BiFunction<>() {
                                            @Override
                                            public ClassVisitor apply(String s, ClassVisitor classVisitor) {
                                                return new ClassVisitor(Gizmo.ASM_API_VERSION, classVisitor) {
                                                    @Override
                                                    public void visit(int version, int access, String name, String signature,
                                                            String superName, String[] interfaces) {
                                                        // Modify class access modifier to public
                                                        super.visit(version, Opcodes.ACC_PUBLIC, name, signature, superName,
                                                                interfaces);
                                                    }

                                                    @Override
                                                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                                                        // Modify class constructor access modifier to public
                                                        int methodAccess = name.equals("<init>") ? Opcodes.ACC_PUBLIC : access;
                                                        return super.visitMethod(methodAccess, name, descriptor, signature,
                                                                exceptions);
                                                    }
                                                };
                                            }
                                        });

                                context.produce(buildItem);
                            }
                        }
                    }).produces(BytecodeTransformerBuildItem.class).build();
                }
            }
        };
    }
}
