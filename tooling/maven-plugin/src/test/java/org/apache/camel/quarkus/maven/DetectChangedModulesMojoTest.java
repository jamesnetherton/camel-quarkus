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
package org.apache.camel.quarkus.maven;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DetectChangedModulesMojoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testCategorizeModules() throws Exception {
        // Use reflection to test the private categorizeModules method
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();
        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("categorizeModules", Set.class);
        method.setAccessible(true);

        Set<String> modules = Set.of(
                "extensions/kafka/runtime",
                "extensions-core/core/runtime",
                "integration-tests/kafka",
                "integration-tests-jvm/spring",
                "test-framework/junit5",
                "tooling/maven-plugin",
                "catalog/camel-catalog",
                "other/random");

        @SuppressWarnings("unchecked")
        Map<String, Set<String>> result = (Map<String, Set<String>>) method.invoke(mojo, modules);

        // Verify extensions
        assertTrue(result.get("extensions").contains("extensions/kafka/runtime"));

        // Verify extensions-core
        assertTrue(result.get("extensions-core").contains("extensions-core/core/runtime"));

        // Verify integration-tests (module name only, no prefix)
        assertTrue(result.get("integration-tests").contains("kafka"));

        // Verify integration-tests-jvm (module name only, no prefix)
        assertTrue(result.get("integration-tests-jvm").contains("spring"));

        // Verify test-framework
        assertTrue(result.get("test-framework").contains("test-framework/junit5"));

        // Verify tooling
        assertTrue(result.get("tooling").contains("tooling/maven-plugin"));

        // Verify catalog
        assertTrue(result.get("catalog").contains("catalog/camel-catalog"));

        // Verify other
        assertTrue(result.get("other").contains("other/random"));
    }

    @Test
    void testGenerateNativeTestsMatrixIncremental() throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        // Set up test categories file
        Path tempDir = Files.createTempDirectory("test-categories");
        File testCategoriesFile = tempDir.resolve("test-categories.yaml").toFile();
        Files.writeString(testCategoriesFile.toPath(),
                "group-01:\n" +
                        "  - kafka\n" +
                        "  - http\n" +
                        "group-02:\n" +
                        "  - aws2-s3\n" +
                        "  - azure\n");

        // Use reflection to set the testCategoriesFile field
        java.lang.reflect.Field field = DetectChangedModulesMojo.class.getDeclaredField("testCategoriesFile");
        field.setAccessible(true);
        field.set(mojo, testCategoriesFile);

        // Use reflection to call generateNativeTestsMatrix
        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("generateNativeTestsMatrix", Set.class, boolean.class);
        method.setAccessible(true);

        Set<String> affectedModules = Set.of("kafka", "aws2-s3");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(mojo, affectedModules, false);

        // Verify structure
        assertTrue(result.containsKey("include"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> include = (List<Map<String, Object>>) result.get("include");

        assertEquals(2, include.size());

        // Verify group-01 contains kafka
        Map<String, Object> group01 = include.stream()
                .filter(g -> "group-01".equals(g.get("category")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> group01Modules = (List<String>) group01.get("modules");
        assertTrue(group01Modules.contains("kafka"));

        // Verify group-02 contains aws2-s3
        Map<String, Object> group02 = include.stream()
                .filter(g -> "group-02".equals(g.get("category")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> group02Modules = (List<String>) group02.get("modules");
        assertTrue(group02Modules.contains("aws2-s3"));

        // Clean up
        Files.deleteIfExists(testCategoriesFile.toPath());
        Files.deleteIfExists(tempDir);
    }

    @Test
    void testGenerateNativeTestsMatrixFullBuild() throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        // Set up test categories file
        Path tempDir = Files.createTempDirectory("test-categories");
        File testCategoriesFile = tempDir.resolve("test-categories.yaml").toFile();
        Files.writeString(testCategoriesFile.toPath(),
                "group-01:\n" +
                        "  - kafka\n" +
                        "  - http\n" +
                        "group-02:\n" +
                        "  - aws2-s3\n");

        java.lang.reflect.Field field = DetectChangedModulesMojo.class.getDeclaredField("testCategoriesFile");
        field.setAccessible(true);
        field.set(mojo, testCategoriesFile);

        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("generateNativeTestsMatrix", Set.class, boolean.class);
        method.setAccessible(true);

        // Full build with empty affected modules
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(mojo, Set.of(), true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> include = (List<Map<String, Object>>) result.get("include");

        assertEquals(2, include.size());

        // Verify group-01 contains ALL modules
        Map<String, Object> group01 = include.stream()
                .filter(g -> "group-01".equals(g.get("category")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> group01Modules = (List<String>) group01.get("modules");
        assertEquals(2, group01Modules.size());
        assertTrue(group01Modules.contains("kafka"));
        assertTrue(group01Modules.contains("http"));

        // Clean up
        Files.deleteIfExists(testCategoriesFile.toPath());
        Files.deleteIfExists(tempDir);
    }

    @Test
    void testGenerateFunctionalTestsConfigIncremental() throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        // Use reflection to test generateFunctionalTestsConfig
        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("generateFunctionalTestsConfig", Map.class, boolean.class);
        method.setAccessible(true);

        Map<String, Set<String>> categorizedModules = Map.of(
                "extensions-core", Set.of("extensions-core/core"),
                "extensions", Set.of(),
                "test-framework", Set.of("test-framework/junit5"),
                "tooling", Set.of(),
                "catalog", Set.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(mojo, categorizedModules, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> include = (List<Map<String, Object>>) result.get("include");

        assertEquals(1, include.size());
        Map<String, Object> item = include.get(0);
        assertEquals("", item.get("category"));

        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) item.get("modules");
        assertEquals(2, modules.size());
        assertTrue(modules.contains("extensions-core"));
        assertTrue(modules.contains("test-framework"));
        assertFalse(modules.contains("extensions"));
        assertFalse(modules.contains("tooling"));
    }

    @Test
    void testGenerateFunctionalTestsConfigFullBuild() throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("generateFunctionalTestsConfig", Map.class, boolean.class);
        method.setAccessible(true);

        // Empty categorized modules for full build
        Map<String, Set<String>> categorizedModules = Map.of(
                "extensions-core", Set.of(),
                "extensions", Set.of(),
                "test-framework", Set.of(),
                "tooling", Set.of(),
                "catalog", Set.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(mojo, categorizedModules, true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> include = (List<Map<String, Object>>) result.get("include");

        assertEquals(1, include.size());
        Map<String, Object> item = include.get(0);

        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) item.get("modules");
        assertEquals(5, modules.size());
        assertTrue(modules.contains("extensions-core"));
        assertTrue(modules.contains("extensions"));
        assertTrue(modules.contains("test-framework"));
        assertTrue(modules.contains("tooling"));
        assertTrue(modules.contains("catalog"));
    }

    @Test
    void testGenerateAlternativeJdkConfigGrouping() throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("generateAlternativeJdkConfig", Set.class, boolean.class);
        method.setAccessible(true);

        Set<String> modules = Set.of("kafka", "http", "aws2-s3", "azure", "mysql");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(mojo, modules, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> include = (List<Map<String, Object>>) result.get("include");

        assertEquals(2, include.size());

        // Verify group-01
        Map<String, Object> group01 = include.stream()
                .filter(g -> "group-01".equals(g.get("category")))
                .findFirst()
                .orElseThrow();

        // Verify group-02
        Map<String, Object> group02 = include.stream()
                .filter(g -> "group-02".equals(g.get("category")))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> group01Modules = (List<String>) group01.get("modules");
        @SuppressWarnings("unchecked")
        List<String> group02Modules = (List<String>) group02.get("modules");

        // Should split roughly in half (5 modules -> 2 and 3)
        assertTrue(group01Modules.size() == 2 || group01Modules.size() == 3);
        assertTrue(group02Modules.size() == 2 || group02Modules.size() == 3);
        assertEquals(5, group01Modules.size() + group02Modules.size());
    }

    @Test
    void testGetAllModulesInDirectory(@TempDir Path tempDir) throws Exception {
        DetectChangedModulesMojo mojo = new DetectChangedModulesMojo();

        // Create mock project structure
        Path integrationTestsDir = tempDir.resolve("integration-tests");
        Files.createDirectories(integrationTestsDir);

        // Create valid modules (with pom.xml)
        Path kafkaModule = integrationTestsDir.resolve("kafka");
        Files.createDirectories(kafkaModule);
        Files.writeString(kafkaModule.resolve("pom.xml"), "<project></project>");

        Path httpModule = integrationTestsDir.resolve("http");
        Files.createDirectories(httpModule);
        Files.writeString(httpModule.resolve("pom.xml"), "<project></project>");

        // Create directory without pom.xml (should be ignored)
        Path notAModule = integrationTestsDir.resolve("docs");
        Files.createDirectories(notAModule);

        // Create hidden directory (should be ignored)
        Path hiddenDir = integrationTestsDir.resolve(".git");
        Files.createDirectories(hiddenDir);

        // Create target directory (should be ignored)
        Path targetDir = integrationTestsDir.resolve("target");
        Files.createDirectories(targetDir);

        // Set up mojo with temp project basedir
        java.lang.reflect.Field projectField = DetectChangedModulesMojo.class.getDeclaredField("project");
        projectField.setAccessible(true);
        org.apache.maven.project.MavenProject mockProject = new org.apache.maven.project.MavenProject();
        mockProject.setFile(tempDir.resolve("pom.xml").toFile());
        projectField.set(mojo, mockProject);

        // Use reflection to call getAllModulesInDirectory
        java.lang.reflect.Method method = DetectChangedModulesMojo.class
                .getDeclaredMethod("getAllModulesInDirectory", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(mojo, "integration-tests/");

        assertEquals(2, result.size());
        assertTrue(result.contains("kafka"));
        assertTrue(result.contains("http"));
        assertFalse(result.contains("docs"));
        assertFalse(result.contains(".git"));
        assertFalse(result.contains("target"));
    }

    @Test
    void testJsonOutputStructure() throws Exception {
        // Verify that the JSON output has the expected structure
        String jsonOutput = """
                {
                  "full-build": false,
                  "changed-modules": ["extensions/kafka"],
                  "native-tests": {
                    "include": [
                      {"category": "group-01", "modules": ["kafka"]}
                    ]
                  },
                  "functional-extension-tests": {
                    "include": [
                      {"category": "", "modules": ["extensions"]}
                    ]
                  },
                  "extensions-jvm-tests": {
                    "include": [
                      {"category": "", "modules": ["spring"]}
                    ]
                  },
                  "integration-tests-alternative-jdk": {
                    "include": [
                      {"category": "group-01", "modules": ["kafka"]}
                    ]
                  }
                }
                """;

        Map<?, ?> parsed = objectMapper.readValue(jsonOutput, Map.class);

        // Verify top-level keys
        assertTrue(parsed.containsKey("full-build"));
        assertTrue(parsed.containsKey("changed-modules"));
        assertTrue(parsed.containsKey("native-tests"));
        assertTrue(parsed.containsKey("functional-extension-tests"));
        assertTrue(parsed.containsKey("extensions-jvm-tests"));
        assertTrue(parsed.containsKey("integration-tests-alternative-jdk"));

        // Verify all test categories have "include" array
        for (String key : Arrays.asList("native-tests", "functional-extension-tests",
                "extensions-jvm-tests", "integration-tests-alternative-jdk")) {
            @SuppressWarnings("unchecked")
            Map<String, ?> testCategory = (Map<String, ?>) parsed.get(key);
            assertTrue(testCategory.containsKey("include"), key + " should have 'include' field");
            assertTrue(testCategory.get("include") instanceof List, key + ".include should be a list");
        }
    }
}
