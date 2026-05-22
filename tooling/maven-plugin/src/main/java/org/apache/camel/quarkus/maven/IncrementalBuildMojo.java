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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Unified mojo for incremental build analysis and matrix generation.
 * <p>
 * Supports multiple actions via the {@code -Dcq.action} parameter:
 * <ul>
 * <li>{@code analyze} - Performs all analysis operations and outputs comprehensive JSON (recommended)</li>
 * <li>{@code filter-modules} - Extracts affected modules from Scalpel report</li>
 * <li>{@code native-matrix} - Generates native test matrix with balanced distribution</li>
 * <li>{@code alternate-jvm-matrix} - Generates alternate JVM test matrix</li>
 * <li>{@code functional-scope} - Detects which functional test scopes are affected</li>
 * <li>{@code jvm-tests} - Detects affected JVM-only test modules</li>
 * </ul>
 * <p>
 * Usage:
 *
 * <pre>
 * mvn org.apache.camel.quarkus:camel-quarkus-maven-plugin:incremental-build \
 *   -Dcq.action=analyze \
 *   -Dcq.useIncrementalBuild=true \
 *   -N
 * </pre>
 */
@Mojo(name = "incremental-build", threadSafe = true, requiresProject = false)
public class IncrementalBuildMojo extends AbstractMojo {

    private static final TypeReference<Map<String, Object>> JSON_TYPE_REF = new TypeReference<Map<String, Object>>() {
    };

    /**
     * Action to perform. Supported values:
     * <ul>
     * <li>analyze - Full analysis (all operations)</li>
     * <li>filter-modules - Extract affected modules</li>
     * <li>native-matrix - Generate native test matrix</li>
     * <li>alternate-jvm-matrix - Generate alternate JVM matrix</li>
     * <li>functional-scope - Detect functional test scope</li>
     * <li>jvm-tests - Detect JVM-only tests</li>
     * </ul>
     */
    @Parameter(property = "cq.action", required = true)
    String action;

    /**
     * Path to Scalpel's JSON report file
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/target/scalpel-report.json", property = "cq.scalpelReportJson")
    Path scalpelReportJson;

    /**
     * Path to test-categories.yaml file (for full builds)
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/tooling/scripts/test-categories.yaml", property = "cq.testCategoriesFile")
    Path testCategoriesFile;

    /**
     * Path to write the output JSON file
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/target/incremental-build.json", property = "cq.outputFile")
    Path outputFile;

    /**
     * Whether to use incremental build (true) or full build (false)
     */
    @Parameter(property = "cq.useIncrementalBuild", defaultValue = "false")
    boolean useIncrementalBuild;

    /**
     * Maximum number of groups for native test matrix distribution
     */
    @Parameter(property = "cq.maxGroups", defaultValue = "13")
    int maxGroups;

    /**
     * Number of groups for alternate JVM matrix
     */
    @Parameter(property = "cq.numGroups", defaultValue = "2")
    int numGroups;

    /**
     * Maximum allowed matrix size (validation)
     */
    @Parameter(property = "cq.maxMatrixSize", defaultValue = "20")
    int maxMatrixSize;

    /**
     * Output compact JSON (single line)
     */
    @Parameter(property = "cq.outputCompact", defaultValue = "true")
    boolean outputCompact;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Map<String, Object> result;

            switch (action) {
            case "analyze":
                result = performFullAnalysis();
                break;
            case "filter-modules":
                result = filterModules();
                break;
            case "native-matrix":
                result = generateNativeMatrix();
                break;
            case "alternate-jvm-matrix":
                result = generateAlternateJvmMatrix();
                break;
            case "functional-scope":
                result = detectFunctionalScope();
                break;
            case "jvm-tests":
                result = detectJvmTests();
                break;
            default:
                throw new MojoExecutionException("Unknown action: " + action + ". Supported: analyze, filter-modules, "
                        + "native-matrix, alternate-jvm-matrix, functional-scope, jvm-tests");
            }

            writeOutput(result);
            getLog().info("Incremental build analysis complete (action=" + action + ")");

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute incremental build analysis", e);
        }
    }

    /**
     * Performs full analysis - all operations in one call.
     * Returns comprehensive JSON with all incremental build data.
     */
    private Map<String, Object> performFullAnalysis() throws IOException, MojoExecutionException {
        Map<String, Object> result = new LinkedHashMap<>();

        // Filter modules
        Map<String, Object> moduleData = filterModules();
        result.put("incrementalBuild", moduleData.get("incrementalBuild"));
        result.put("affectedModulesCount", moduleData.get("totalModules"));
        result.put("affectedModules", moduleData.get("modules"));

        // Generate native test matrix
        Map<String, Object> nativeMatrix = generateNativeMatrix();
        result.put("nativeTestMatrix", nativeMatrix);

        // Generate alternate JVM matrix
        Map<String, Object> altJvmMatrix = generateAlternateJvmMatrix();
        result.put("alternateJvmMatrix", altJvmMatrix);

        // Detect functional test scope
        Map<String, Object> functionalScope = detectFunctionalScope();
        result.put("functionalTestScope", functionalScope);

        // Detect JVM-only tests
        Map<String, Object> jvmTests = detectJvmTests();
        result.put("jvmOnlyTests", jvmTests);

        return result;
    }

    /**
     * Extracts affected modules from Scalpel report.
     * Implements two-pass filtering logic from FilterCategoriesMojo.
     */
    private Map<String, Object> filterModules() throws IOException, MojoExecutionException {
        Map<String, Object> result = new LinkedHashMap<>();

        // Check if we should do incremental build
        if (!useIncrementalBuild || !Files.exists(scalpelReportJson)) {
            getLog().info("Full build mode (useIncrementalBuild=" + useIncrementalBuild + ")");
            result.put("incrementalBuild", false);
            result.put("modules", new ArrayList<>());
            result.put("totalModules", 0);
            return result;
        }

        // Read Scalpel report
        Map<String, Object> scalpelReport = jsonMapper.readValue(scalpelReportJson.toFile(), JSON_TYPE_REF);

        // Check if Scalpel triggered full build
        Boolean fullBuildTriggered = (Boolean) scalpelReport.get("fullBuildTriggered");
        if (Boolean.TRUE.equals(fullBuildTriggered)) {
            getLog().info("Full build triggered by Scalpel");
            result.put("incrementalBuild", false);
            result.put("modules", new ArrayList<>());
            result.put("totalModules", 0);
            return result;
        }

        // Extract affected modules
        List<Map<String, Object>> affectedModules = (List<Map<String, Object>>) scalpelReport.get("affectedModules");
        if (affectedModules == null || affectedModules.isEmpty()) {
            getLog().info("No affected modules - using full build for safety");
            result.put("incrementalBuild", false);
            result.put("modules", new ArrayList<>());
            result.put("totalModules", 0);
            return result;
        }

        // Extract affected integration tests with two-pass filtering
        Set<String> affectedTests = extractAffectedTests(affectedModules);

        result.put("incrementalBuild", true);
        result.put("modules", new ArrayList<>(affectedTests));
        result.put("totalModules", affectedTests.size());

        getLog().info("Incremental build: " + affectedTests.size() + " affected modules");
        return result;
    }

    /**
     * Extracts affected integration test modules using two-pass filtering.
     * Pass 1: Check if any extensions were directly changed.
     * Pass 2: Include integration tests based on category and extension changes.
     */
    private Set<String> extractAffectedTests(List<Map<String, Object>> affectedModules) {
        Set<String> affectedTests = new LinkedHashSet<>();

        // First pass: Check if any extensions or extensions-jvm were directly changed
        boolean extensionChanged = false;
        for (Map<String, Object> module : affectedModules) {
            String category = (String) module.get("category");
            String path = (String) module.get("path");

            if ("DIRECT".equals(category) && path != null) {
                if (path.startsWith("extensions/") ||
                        path.startsWith("extensions-jvm/") ||
                        path.startsWith("extensions-core/")) {
                    extensionChanged = true;
                    getLog().debug("Extension change detected: " + path);
                    break;
                }
            }
        }

        // Second pass: Include integration tests based on category and source of change
        for (Map<String, Object> module : affectedModules) {
            String path = (String) module.get("path");
            String category = (String) module.get("category");

            if (path != null && path.startsWith("integration-tests/")) {
                boolean shouldInclude = false;

                // Always include DIRECT changes to integration tests
                if ("DIRECT".equals(category)) {
                    shouldInclude = true;
                    getLog().debug("Including test (direct change): " + path);
                }
                // Include DOWNSTREAM if an extension was changed
                else if ("DOWNSTREAM".equals(category) && extensionChanged) {
                    shouldInclude = true;
                    getLog().debug("Including test (downstream from extension): " + path);
                }
                // Otherwise skip (e.g., downstream from test-framework changes)
                else {
                    getLog().debug("Skipping test (downstream from infrastructure): " + path);
                }

                if (shouldInclude) {
                    // Extract test name: integration-tests/box -> box
                    String testName = path.substring("integration-tests/".length());
                    // Remove any trailing path components
                    if (testName.contains("/")) {
                        testName = testName.substring(0, testName.indexOf("/"));
                    }
                    affectedTests.add(testName);
                }
            }
        }

        return affectedTests;
    }

    /**
     * Generates native test matrix with balanced distribution across groups.
     */
    private Map<String, Object> generateNativeMatrix() throws IOException, MojoExecutionException {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!useIncrementalBuild) {
            // Full build - return empty matrix (workflow will use categories from YAML)
            result.put("include", new ArrayList<>());
            return result;
        }

        // Get affected modules
        Map<String, Object> moduleData = filterModules();
        List<String> modules = (List<String>) moduleData.get("modules");

        if (modules == null || modules.isEmpty()) {
            result.put("include", new ArrayList<>());
            return result;
        }

        // Distribute modules across balanced groups
        int moduleCount = modules.size();
        int groups = Math.min(moduleCount, maxGroups);
        int modulesPerGroup = (int) Math.ceil((double) moduleCount / groups);

        List<Map<String, String>> include = new ArrayList<>();
        for (int i = 0; i < groups; i++) {
            int start = i * modulesPerGroup;
            int end = Math.min(start + modulesPerGroup, moduleCount);

            if (start < moduleCount) {
                List<String> groupModules = modules.subList(start, end);
                Map<String, String> group = new LinkedHashMap<>();
                group.put("name", "group-" + (i + 1));
                group.put("modules", String.join(",", groupModules));
                include.add(group);
            }
        }

        // Validate matrix size
        if (include.size() > maxMatrixSize) {
            throw new MojoExecutionException(
                    "Native test matrix size (" + include.size() + ") exceeds maximum (" + maxMatrixSize + ")");
        }

        result.put("include", include);
        getLog().info("Native test matrix: " + include.size() + " groups for " + moduleCount + " modules");
        return result;
    }

    /**
     * Generates alternate JVM matrix by splitting modules into N groups.
     */
    private Map<String, Object> generateAlternateJvmMatrix() throws IOException, MojoExecutionException {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!useIncrementalBuild) {
            // Full build - return empty matrix (workflow will use all modules)
            result.put("include", new ArrayList<>());
            return result;
        }

        // Get affected modules
        Map<String, Object> moduleData = filterModules();
        List<String> modules = (List<String>) moduleData.get("modules");

        if (modules == null || modules.isEmpty()) {
            result.put("include", new ArrayList<>());
            return result;
        }

        // Split modules into numGroups
        int moduleCount = modules.size();
        int actualGroups = Math.min(moduleCount, numGroups);
        int modulesPerGroup = (int) Math.ceil((double) moduleCount / actualGroups);

        List<Map<String, String>> include = new ArrayList<>();
        for (int i = 0; i < actualGroups; i++) {
            int start = i * modulesPerGroup;
            int end = Math.min(start + modulesPerGroup, moduleCount);

            if (start < moduleCount) {
                List<String> groupModules = modules.subList(start, end);
                Map<String, String> group = new LinkedHashMap<>();
                group.put("name", "group-" + String.format("%02d", i + 1));
                group.put("modules", String.join(",", groupModules));
                include.add(group);
            }
        }

        result.put("include", include);
        getLog().info("Alternate JVM matrix: " + include.size() + " groups for " + moduleCount + " modules");
        return result;
    }

    /**
     * Detects which functional test scopes are affected by analyzing DIRECT changes.
     */
    private Map<String, Object> detectFunctionalScope() throws IOException {
        Map<String, Boolean> scope = new LinkedHashMap<>();
        scope.put("runExtensionsCoreTests", false);
        scope.put("runExtensionsTests", false);
        scope.put("runTestFrameworkTests", false);
        scope.put("runToolingTests", false);
        scope.put("runCatalogTests", false);

        if (!useIncrementalBuild || !Files.exists(scalpelReportJson)) {
            // Full build - run all tests
            scope.replaceAll((k, v) -> true);
            return new LinkedHashMap<>(scope);
        }

        // Read Scalpel report
        Map<String, Object> scalpelReport = jsonMapper.readValue(scalpelReportJson.toFile(), JSON_TYPE_REF);
        List<Map<String, Object>> affectedModules = (List<Map<String, Object>>) scalpelReport.get("affectedModules");

        if (affectedModules == null) {
            return new LinkedHashMap<>(scope);
        }

        // Filter to DIRECT changes only
        for (Map<String, Object> module : affectedModules) {
            String category = (String) module.get("category");
            if (!"DIRECT".equals(category)) {
                continue;
            }

            String path = (String) module.get("path");
            if (path == null) {
                continue;
            }

            // Categorize by path prefix
            if (path.startsWith("extensions-core/")) {
                scope.put("runExtensionsCoreTests", true);
            } else if (path.startsWith("extensions/") || path.startsWith("extensions-jvm/")) {
                scope.put("runExtensionsTests", true);
            } else if (path.startsWith("test-framework/")) {
                scope.put("runTestFrameworkTests", true);
            } else if (path.startsWith("tooling/")) {
                scope.put("runToolingTests", true);
            } else if (path.startsWith("catalog/")) {
                scope.put("runCatalogTests", true);
            }
        }

        getLog().info("Functional test scope: extensions-core=" + scope.get("runExtensionsCoreTests")
                + ", extensions=" + scope.get("runExtensionsTests")
                + ", test-framework=" + scope.get("runTestFrameworkTests")
                + ", tooling=" + scope.get("runToolingTests")
                + ", catalog=" + scope.get("runCatalogTests"));

        return new LinkedHashMap<>(scope);
    }

    /**
     * Detects affected JVM-only test modules.
     */
    private Map<String, Object> detectJvmTests() throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runJvmTests", false);
        result.put("jvmModules", "");

        if (!useIncrementalBuild || !Files.exists(scalpelReportJson)) {
            // Full build - run all JVM tests
            result.put("runJvmTests", true);
            return result;
        }

        // Read Scalpel report
        Map<String, Object> scalpelReport = jsonMapper.readValue(scalpelReportJson.toFile(), JSON_TYPE_REF);
        List<Map<String, Object>> affectedModules = (List<Map<String, Object>>) scalpelReport.get("affectedModules");

        if (affectedModules == null || affectedModules.isEmpty()) {
            return result;
        }

        // Extract JVM-only test modules
        List<String> jvmModules = new ArrayList<>();
        for (Map<String, Object> module : affectedModules) {
            String path = (String) module.get("path");
            if (path != null && path.startsWith("integration-tests-jvm/")) {
                String moduleName = path.substring("integration-tests-jvm/".length());
                // Remove any trailing path components
                if (moduleName.contains("/")) {
                    moduleName = moduleName.substring(0, moduleName.indexOf("/"));
                }
                if (!jvmModules.contains(moduleName)) {
                    jvmModules.add(moduleName);
                }
            }
        }

        if (!jvmModules.isEmpty()) {
            result.put("runJvmTests", true);
            result.put("jvmModules", String.join(",", jvmModules));
            getLog().info("JVM-only tests: " + jvmModules.size() + " modules affected");
        }

        return result;
    }

    /**
     * Writes output JSON to file.
     */
    private void writeOutput(Map<String, Object> data) throws IOException {
        Files.createDirectories(outputFile.getParent());

        String json;
        if (outputCompact) {
            json = jsonMapper.writeValueAsString(data);
        } else {
            json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        }

        Files.write(outputFile, json.getBytes(StandardCharsets.UTF_8));
        getLog().debug("Written output to: " + outputFile);
    }
}
