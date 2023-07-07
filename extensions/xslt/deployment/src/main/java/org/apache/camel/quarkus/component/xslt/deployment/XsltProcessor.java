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
package org.apache.camel.quarkus.component.xslt.deployment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.runtime.RuntimeValue;
import org.apache.camel.component.xslt.XsltComponent;
import org.apache.camel.quarkus.component.xslt.CamelXsltConfig;
import org.apache.camel.quarkus.component.xslt.CamelXsltErrorListener;
import org.apache.camel.quarkus.component.xslt.CamelXsltRecorder;
import org.apache.camel.quarkus.component.xslt.RuntimeUriResolver.Builder;
import org.apache.camel.quarkus.component.xslt.deployment.BuildTimeUriResolver.ResolutionResult;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilter;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilterBuildItem;
import org.apache.camel.quarkus.support.xalan.XalanTransformerFactory;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

class XsltProcessor {
    // The default XSLT generated class package name defined in com.sun.org.apache.xalan.internal.xsltc.compiler.XSLTC
    // Due to a bug in the JDK, the package is not overridable. See https://github.com/apache/camel-quarkus/issues/4065
    private static final String XSLTC_DEFAULT_PACKAGE_NAME = "die_verwandlung";
    private static final String XSLTC_DEFAULT_CLASS_NAME = XSLTC_DEFAULT_PACKAGE_NAME + ".class";

    /*
     * The xslt component is programmatically configured by the extension thus
     * we can safely prevent camel to instantiate a default instance.
     */
    @BuildStep
    CamelServiceFilterBuildItem serviceFilter() {
        return new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("xslt"));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelBeanBuildItem xsltComponent(
            CamelXsltRecorder recorder,
            CamelXsltConfig config,
            PackageConfig packageConfig,
            List<UriResolverEntryBuildItem> uriResolverEntries) {
        packageConfig.manifest.attributes.put("Add-Exports",
                "java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime java.xml/com.sun.org.apache.xalan.internal.xsltc.dom java.xml/com.sun.org.apache.xalan.internal.xsltc java.xml/com.sun.org.apache.xml.internal.dtm java.xml/com.sun.org.apache.xml.internal.serializer");
        final RuntimeValue<Builder> builder = recorder.createRuntimeUriResolverBuilder();
        for (UriResolverEntryBuildItem entry : uriResolverEntries) {
            recorder.addRuntimeUriResolverEntry(
                    builder,
                    entry.getTemplateUri(),
                    entry.getTransletClassName());
        }

        return new CamelBeanBuildItem(
                "xslt",
                XsltComponent.class.getName(),
                recorder.createXsltComponent(config, builder));
    }

    @BuildStep
    void xsltResources(
            CamelXsltConfig config,
            BuildProducer<XsltGeneratedClassBuildItem> generatedNames,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<UriResolverEntryBuildItem> uriResolverEntries) throws Exception {

        final Path destination = Files.createTempDirectory(XsltFeature.FEATURE);
        final Set<String> translets = new LinkedHashSet<>();
        try {
            final BuildTimeUriResolver resolver = new BuildTimeUriResolver();
            for (String uri : config.sources.orElse(Collections.emptyList())) {
                ResolutionResult resolvedUri = resolver.resolve(uri);
                uriResolverEntries.produce(resolvedUri.toBuildItem());

                if (translets.contains(resolvedUri.transletClassName)) {
                    throw new RuntimeException("XSLT translet name clash: cannot add '" + resolvedUri.transletClassName
                            + "' to previously added translets " + translets);
                }

                translets.add(resolvedUri.transletClassName);

                try {
                    TransformerFactory tf = new XalanTransformerFactory();

                    for (Map.Entry<String, Boolean> entry : config.features.entrySet()) {
                        tf.setFeature(entry.getKey(), entry.getValue());
                    }

                    tf.setAttribute("generate-translet", true);
                    tf.setAttribute("translet-name", resolvedUri.transletClassName);
                    tf.setAttribute("package-name", config.packageName);
                    tf.setAttribute("destination-directory", destination.toString());
                    tf.setErrorListener(new CamelXsltErrorListener());
                    tf.newTemplates(resolvedUri.source);

                    // Rename classes files with the correct name
                    String packagePath = config.packageName.replace(".", "/");
                    Path source = destination.resolve(packagePath + "/" + XSLTC_DEFAULT_CLASS_NAME);
                    Path target = destination
                            .resolve(packagePath + "/" + StringHelper.capitalize(resolvedUri.transletClassName) + ".class");
                    Files.move(source, target);
                } catch (TransformerException e) {
                    throw new RuntimeException("Could not compile XSLT " + uri, e);
                }
            }

            try (Stream<Path> files = Files.walk(destination)) {
                files
                        .sorted(Comparator.reverseOrder())
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                final Path rel = destination.relativize(path);
                                final String fqcn = StringUtils.removeEnd(rel.toString(), ".class").replace(File.separatorChar,
                                        '.');
                                final String badClass = config.packageName.replace('.', '/') + "/" + XSLTC_DEFAULT_PACKAGE_NAME;
                                final String correctClass = fqcn.replace('.', '/');

                                // TODO: This should be removed https://github.com/apache/camel-quarkus/issues/4065
                                // Transform the generated class to remove die_verwandlung class and package names
                                final byte[] data = Files.readAllBytes(path);
                                ClassReader reader = new ClassReader(data);
                                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                                ClassVisitor visitor = new ClassRemapper(writer,
                                        new TransletClassRemapper(
                                                badClass,
                                                correctClass));
                                reader.accept(visitor, ClassReader.EXPAND_FRAMES);

                                generatedClasses.produce(new GeneratedClassBuildItem(false, fqcn, writer.toByteArray()));
                                generatedNames.produce(new XsltGeneratedClassBuildItem(fqcn));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        } finally {
            try (Stream<Path> files = Files.walk(destination)) {
                files.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    static final class TransletClassRemapper extends Remapper {
        private final String oldClassName;
        private final String newClassName;
        private final String oldClassNameFqcn;
        private final String newClassNameFqcn;

        private TransletClassRemapper(String oldClassName, String newClassName) {
            this.oldClassName = oldClassName;
            this.newClassName = newClassName;
            this.oldClassNameFqcn = oldClassName.replace('/', '.');
            this.newClassNameFqcn = newClassName.replace('/', '.');
        }

        @Override
        public String map(String className) {
            if (className.equals(oldClassName)) {
                return newClassName;
            }
            return className;
        }

        @Override
        public Object mapValue(Object value) {
            if (value.equals(oldClassNameFqcn)) {
                return super.mapValue(newClassNameFqcn);
            }
            return super.mapValue(value);
        }
    }
}
