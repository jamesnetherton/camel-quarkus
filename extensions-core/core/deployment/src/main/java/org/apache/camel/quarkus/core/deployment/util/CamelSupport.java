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
package org.apache.camel.quarkus.core.deployment.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.AbstractCamelContext;
import org.apache.camel.quarkus.core.CamelCapabilities;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

public final class CamelSupport {
    public static final String CAMEL_SERVICE_BASE_PATH = "META-INF/services/org/apache/camel";
    public static final String CAMEL_ROOT_PACKAGE_DIRECTORY = "org/apache/camel";
    public static final String CLASSPATH_PREFIX = "classpath:";
    public static final String COMPILATION_JVM_TARGET = "17";

    private CamelSupport() {
    }

    public static boolean isConcrete(ClassInfo ci) {
        return (ci.flags() & Modifier.ABSTRACT) == 0;
    }

    public static boolean isPublic(ClassInfo ci) {
        return (ci.flags() & Modifier.PUBLIC) != 0;
    }

    public static Stream<CamelServiceBuildItem> services(ApplicationArchivesBuildItem archives, PathFilter pathFilter) {
        final Set<CamelServiceBuildItem> answer = new HashSet<>();
        final Predicate<Path> filter = pathFilter.asPathPredicate();

        for (ApplicationArchive archive : archives.getAllApplicationArchives()) {
            for (Path root : archive.getRootDirectories()) {
                final Path resourcePath = root.resolve(CAMEL_SERVICE_BASE_PATH);

                if (!Files.isDirectory(resourcePath)) {
                    continue;
                }

                try (Stream<Path> files = Files.walk(resourcePath)) {
                    files.filter(Files::isRegularFile).forEach(file -> {
                        // the root archive may point to a jar file or the absolute path of
                        // a project's build output so we need to relativize to make the
                        // FastFactoryFinder work as expected
                        Path key = root.relativize(file);

                        if (filter.test(key)) {
                            String clazz = readProperties(file).getProperty("class");
                            if (clazz != null) {
                                answer.add(new CamelServiceBuildItem(key, clazz));
                            }
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException("Could not walk " + resourcePath, e);
                }
            }
        }

        return answer.stream();
    }

    private static Properties readProperties(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            final Properties result = new Properties();
            result.load(in);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + path, e);
        }
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        return Stream.of(items).collect(Collectors.toCollection(HashSet::new));
    }

    public static String getCamelVersion() {
        String version = null;

        Package aPackage = AbstractCamelContext.class.getPackage();
        if (aPackage != null) {
            version = aPackage.getImplementationVersion();
            if (version == null) {
                version = aPackage.getSpecificationVersion();
            }
        }

        return Objects.requireNonNull(version, "Could not determine Camel version");
    }

    public static <T> T getOptionalConfigValue(String property, Class<T> type, T defaultValue) {
        return ConfigProvider.getConfig()
                .getOptionalValue(property, type)
                .orElse(defaultValue);
    }

    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static CamelContext newBuildTimeCamelContext(boolean init) {
        CamelContext context = new DefaultCamelContext(false);
        if (init) {
            context.init();
        }
        return context;
    }

    public static String stripClasspathScheme(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return path.substring(CLASSPATH_PREFIX.length());
        }
        return path;
    }

    public static boolean isRouteResourceDslCapabilitiesPresent(Capabilities capabilities) {
        return capabilities.isPresent(CamelCapabilities.XML_JAXB)
                || capabilities.isPresent(CamelCapabilities.XML_IO_DSL)
                || capabilities.isPresent(CamelCapabilities.YAML_DSL)
                || capabilities.isPresent(CamelCapabilities.JAVA_JOOR_DSL);
    }

    public static Set<String> getRouteResourceFileExtensions(Capabilities capabilities) {
        Set<String> extensions = new HashSet<>();
        if (capabilities.isPresent(CamelCapabilities.XML_JAXB) || capabilities.isPresent(CamelCapabilities.XML_IO_DSL)) {
            extensions.add("xml");
            extensions.add("camel.xml");
        }

        if (capabilities.isPresent(CamelCapabilities.YAML_DSL)) {
            extensions.add("yaml");
            extensions.add("camel.yaml");
        }

        if (capabilities.isPresent(CamelCapabilities.JAVA_JOOR_DSL)) {
            extensions.add("java");
        }

        return extensions;
    }

    /**
     * Checks whether the given class name is assignable to (i.e., is a subclass of or implements)
     * the specified superclass or interface. First checks the Jandex index (covers application
     * and indexed dependency classes), then falls back to Class.forName() for JDK/unindexed classes.
     *
     * @param  className  the fully-qualified class name to check
     * @param  superClass the superclass or interface to check against
     * @param  index      the Jandex index
     * @return            true if className is assignable to superClass
     */
    public static boolean isAssignableTo(String className, Class<?> superClass, IndexView index) {
        ClassInfo classInfo = index.getClassByName(DotName.createSimple(className));
        if (classInfo != null) {
            return isAssignableToInIndex(classInfo, superClass, index);
        }

        // Fall back to Class.forName() for JDK and unindexed library classes
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            return superClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks whether the given ClassInfo represents a class that is assignable to the specified
     * superclass or interface. Walks the class hierarchy using the Jandex index, falling back
     * to Class.forName() when exiting the index.
     *
     * @param  classInfo  the ClassInfo to check
     * @param  superClass the superclass or interface to check against
     * @param  index      the Jandex index
     * @return            true if the class is assignable to superClass
     */
    public static boolean isAssignableToInIndex(ClassInfo classInfo, Class<?> superClass, IndexView index) {
        DotName targetType = DotName.createSimple(superClass.getName());
        DotName current = classInfo.name();

        while (current != null) {
            if (targetType.equals(current)) {
                return true;
            }
            ClassInfo info = index.getClassByName(current);
            if (info == null) {
                // Class not in index, use Class.forName() for the remaining hierarchy
                try {
                    Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(current.toString());
                    return superClass.isAssignableFrom(clazz);
                } catch (ClassNotFoundException e) {
                    return false;
                }
            }
            current = info.superName();
        }
        return false;
    }
}
