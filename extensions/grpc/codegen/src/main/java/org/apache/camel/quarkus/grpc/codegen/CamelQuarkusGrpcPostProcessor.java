package org.apache.camel.quarkus.grpc.codegen;

import java.io.File;
import java.nio.file.Path;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;
import org.jboss.logging.Logger;

/**
 * Post processor for generated proto classes.
 */
public class CamelQuarkusGrpcPostProcessor {
    private static final Logger LOG = Logger.getLogger(CamelQuarkusGrpcPostProcessor.class);
    private static final String JAVAX_GENERATED = "javax" + ".annotation.Generated";
    private static final String JAKARTA_GENERATED = "jakarta.annotation.Generated";
    private final Path root;

    public CamelQuarkusGrpcPostProcessor(Path root) {
        this.root = root;
    }

    public static void main(String[] args) {
        for (String arg : args) {
            Path path = new File(arg).toPath();
            CamelQuarkusGrpcPostProcessor postProcessor = new CamelQuarkusGrpcPostProcessor(path);
            postProcessor.process();
        }
    }

    public void process() {
        SourceRoot sr = new SourceRoot(root);
        try {
            sr.parse("", (localPath, absolutePath, result) -> {
                if (result.isSuccessful()) {
                    CompilationUnit unit = result.getResult().orElseThrow();
                    if (unit.getPrimaryType().isPresent()) {
                        TypeDeclaration<?> type = unit.getPrimaryType().get();
                        process(unit, type);
                        return SourceRoot.Callback.Result.SAVE;
                    }
                } else {
                    LOG.errorf(
                            "Unable to parse a protoc generated class, skipping post-processing for this " +
                                    "file. Reported problems are %s",
                            result.toString());
                }

                return SourceRoot.Callback.Result.DONT_SAVE;
            });
        } catch (Exception e) {
            LOG.error("Unable to parse protoc generated classes - skipping gRPC post processing", e);
        }
    }

    private void process(CompilationUnit unit, TypeDeclaration<?> primary) {
        LOG.debugf("Post-processing %s", primary.getFullyQualifiedName().orElse(primary.getNameAsString()));
        unit.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(NormalAnnotationExpr n, Void arg) {
                // Replace javax.annotation.Generated with the jakarta equivalent
                // https://github.com/grpc/grpc-java/issues/9179
                if (n.getNameAsString().equals(JAVAX_GENERATED)) {
                    n.setName(JAKARTA_GENERATED);
                }
                return super.visit(n, arg);
            }
        }, null);
    }
}
