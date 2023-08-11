package org.apache.camel.quarkus.grpc.protoc.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.compiler.PluginProtos;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtocPlugin;

public class CamelQuarkusProtocPlugin extends Generator {
    private static final String ANNOTATION_GENERATED_SUFFIX = ".annotation.Generated";
    private static final String JAVAX_ANNOTATION_GENERATED = "javax" + ANNOTATION_GENERATED_SUFFIX;
    private static final String JAKARTA_ANNOTATION_GENERATED = "jakarta" + ANNOTATION_GENERATED_SUFFIX;

    public static void main(String args[]) {
        ProtocPlugin.generate(new CamelQuarkusProtocPlugin());
    }

    @Override
    public List<File> generateFiles(PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
        List<DescriptorProtos.FileDescriptorProto> protosToGenerate = request.getProtoFileList().stream()
                .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
                .toList();

        List<File> files = new ArrayList<>();
        protosToGenerate.stream().forEach(fileDescriptorProto -> {
            String content = applyTemplate(fileDescriptorProto.getName(), null);
            File build = File
                    .newBuilder()
                    .setName(fileDescriptorProto.getName())
                    .setContent(content)
                    .build();
            files.add(build);
        });
        return files;
    }

    private File buildFile(Object context, String templateName, String fileName) {
        String content = applyTemplate(templateName, context);
        return File
                .newBuilder()
                .setName(fileName)
                .setContent(content)
                .build();
    }

    @Override
    protected List<PluginProtos.CodeGeneratorResponse.Feature> supportedFeatures() {
        return Collections.singletonList(PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL);
    }

    @Override
    protected File makeFile(String fileName, byte[] fileContent) {
        String foo = new String(fileContent);
        foo = foo.replace(JAVAX_ANNOTATION_GENERATED, JAKARTA_ANNOTATION_GENERATED);
        System.out.println(foo);
        return super.makeFile(fileName, foo);
    }

    @Override
    protected File makeFile(String fileName, String fileContent) {
        if (fileContent != null) {
            fileContent = fileContent.replace(JAVAX_ANNOTATION_GENERATED, JAKARTA_ANNOTATION_GENERATED);
        }
        System.out.println(fileContent);
        return super.makeFile(fileName, fileContent);
    }

    @Override
    protected String applyTemplate(@Nonnull String resourcePath, @Nonnull Object generatorContext) {
        String templateResult = super.applyTemplate(resourcePath, generatorContext);
        if (templateResult != null) {
            templateResult = templateResult.replace(JAVAX_ANNOTATION_GENERATED, JAKARTA_ANNOTATION_GENERATED);
        }
        System.out.println(templateResult);
        return templateResult;
    }
}
