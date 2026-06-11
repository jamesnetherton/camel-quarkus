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
     * Maximum allowed matrix size (validation)
     */
    @Parameter(property = "cq.maxMatrixSize", defaultValue = "20")
    int maxMatrixSize;

    /**
     * Output compact JSON (single line)
     */
    @Parameter(property = "cq.outputCompact", defaultValue = "true")
    boolean outputCompact;

    /**
     * Comma-separated list of extension directory prefixes to detect extension changes.
     * Default: extensions/,extensions-jvm/,extensions-core/
     */
    @Parameter(property = "cq.extensionDirs", defaultValue = "extensions/,extensions-jvm/,extensions-core/")
    String extensionDirs;

    /**
     * Comma-separated list of integration test directory prefixes.
     * Default: integration-tests/,integration-tests-jvm/
     */
    @Parameter(property = "cq.integrationTestDirs", defaultValue = "integration-tests/,integration-tests-jvm/")
    String integrationTestDirs;

    /**
     * Prefix for native-supported integration tests (used for filtering).
     * Default: integration-tests/
     */
    @Parameter(property = "cq.nativeTestsPrefix", defaultValue = "integration-tests/")
    String nativeTestsPrefix;

    /**
     * Prefix for JVM-only integration tests.
     * Default: integration-tests-jvm/
     */
    @Parameter(property = "cq.jvmTestsPrefix", defaultValue = "integration-tests-jvm/")
    String jvmTestsPrefix;

    /**
     * Prefix for grouped integration tests.
     * Path structure: integration-test-groups/&lt;group&gt;/&lt;module&gt;/
     * Default: integration-test-groups/
     */
    @Parameter(property = "cq.integrationTestGroupsPrefix", defaultValue = "integration-test-groups/")
    String integrationTestGroupsPrefix;

    /**
     * Comma-separated list of directory prefixes for functional test scope detection.
     * Format: prefix:scopeName
     * Default:
     * extensions-core/:runExtensionsCoreTests,extensions/:runExtensionsTests,test-framework/:runTestFrameworkTests,tooling/:runToolingTests,catalog/:runCatalogTests
     */
    @Parameter(property = "cq.functionalScopeDirs", defaultValue = "extensions-core/:runExtensionsCoreTests,extensions/:runExtensionsTests,test-framework/:runTestFrameworkTests,tooling/:runToolingTests,catalog/:runCatalogTests")
    String functionalScopeDirs;

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

        // Detect functional test scope
        Map<String, Object> functionalScope = detectFunctionalScope();
        result.put("functionalTestScope", functionalScope);

        // Detect JVM-only tests
        Map<String, Object> jvmTests = detectJvmTests();
        result.put("jvmOnlyTests", jvmTests);

        // Detect if examples should run (only when extensions change, not integration tests)
        boolean runExamples = shouldRunExamples();
        result.put("runExamples", runExamples);

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
     * Extracts affected integration test modules from Scalpel report.
     * Includes both DIRECT and DOWNSTREAM changes - if Scalpel reports it as affected,
     * we should test it.
     */
    private Set<String> extractAffectedTests(List<Map<String, Object>> affectedModules) {
        Set<String> affectedTests = new LinkedHashSet<>();

        for (Map<String, Object> module : affectedModules) {
            String path = (String) module.get("path");
            String category = (String) module.get("category");

            // Handle integration-test-groups: integration-test-groups/<group>/... -> <group>-grouped
            if (path != null && path.startsWith(integrationTestGroupsPrefix)) {
                // Extract group name from: integration-test-groups/<group>/...
                String remainder = path.substring(integrationTestGroupsPrefix.length());
                String[] parts = remainder.split("/");
                if (parts.length >= 1) {
                    String groupName = parts[0]; // Get the group name
                    String groupedModuleName = groupName + "-grouped";
                    affectedTests.add(groupedModuleName);
                    getLog().debug("Including grouped test: " + groupedModuleName + " (category: " + category + ")");
                }
                continue;
            }

            // Handle regular integration-tests
            if (path != null && path.startsWith(nativeTestsPrefix)) {
                // Include both DIRECT and DOWNSTREAM - if Scalpel detected it, test it
                if ("DIRECT".equals(category) || "DOWNSTREAM".equals(category)) {
                    // Extract test name: integration-tests/box -> box
                    String testName = path.substring(nativeTestsPrefix.length());
                    // Remove any trailing path components
                    if (testName.contains("/")) {
                        testName = testName.substring(0, testName.indexOf("/"));
                    }
                    affectedTests.add(testName);
                    getLog().debug("Including test: " + testName + " (category: " + category + ")");
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
    /**
     * Detects which functional test scopes are affected by analyzing DIRECT changes.
     */
    private Map<String, Object> detectFunctionalScope() throws IOException {
        // Parse functional scope configuration: prefix:scopeName,prefix:scopeName,...
        Map<String, String> prefixToScope = new LinkedHashMap<>();
        Map<String, Boolean> scope = new LinkedHashMap<>();

        for (String entry : functionalScopeDirs.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2) {
                String prefix = parts[0].trim();
                String scopeName = parts[1].trim();
                prefixToScope.put(prefix, scopeName);
                scope.put(scopeName, false);
            }
        }

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

            // Check each prefix and set corresponding scope flag
            for (Map.Entry<String, String> entry : prefixToScope.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    scope.put(entry.getValue(), true);
                }
            }
        }

        getLog().info("Functional test scope: " + scope);

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
            if (path != null && path.startsWith(jvmTestsPrefix)) {
                String moduleName = path.substring(jvmTestsPrefix.length());
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
     * Determines if examples should run based on affected modules.
     * Examples should only run when extensions change, NOT when integration tests change.
     *
     * @return true if any extension (runtime or deployment) is affected
     */
    private boolean shouldRunExamples() throws IOException, MojoExecutionException {
        if (!useIncrementalBuild || !Files.exists(scalpelReportJson)) {
            // Full build - run examples
            return true;
        }

        Map<String, Object> scalpelReport = jsonMapper.readValue(scalpelReportJson.toFile(), JSON_TYPE_REF);
        Boolean fullBuildTriggered = (Boolean) scalpelReport.get("fullBuildTriggered");
        if (Boolean.TRUE.equals(fullBuildTriggered)) {
            // Full build - run examples
            return true;
        }

        List<Map<String, Object>> affectedModules = (List<Map<String, Object>>) scalpelReport.get("affectedModules");
        if (affectedModules == null || affectedModules.isEmpty()) {
            // Full build for safety - run examples
            return true;
        }

        // Check if any affected module is an extension (not an integration test)
        for (Map<String, Object> module : affectedModules) {
            String path = (String) module.get("path");
            String category = (String) module.get("category");

            // Only consider DIRECT changes
            if (!"DIRECT".equals(category)) {
                continue;
            }

            // Check if this is an extension change
            if (path != null && (path.startsWith("extensions/") ||
                    path.startsWith("extensions-core/") ||
                    path.startsWith("extensions-jvm/"))) {
                getLog().info("Examples should run - extension affected: " + path);
                return true;
            }
        }

        getLog().info("Examples will be skipped - only integration tests affected");
        return false;
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
