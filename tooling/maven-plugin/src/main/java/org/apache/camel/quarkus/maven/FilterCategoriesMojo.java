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
 * Filters test modules based on Scalpel's change detection report.
 * <p>
 * Reads the Scalpel JSON report to identify affected integration test modules
 * and outputs a list suitable for dynamic matrix generation in GitHub Actions.
 * <p>
 * Usage:
 *
 * <pre>
 * mvn org.apache.camel.quarkus:camel-quarkus-maven-plugin:filter-test-categories -N
 * </pre>
 */
@Mojo(name = "filter-test-categories", threadSafe = true, requiresProject = false)
public class FilterCategoriesMojo extends AbstractMojo {

    private static final TypeReference<Map<String, Object>> JSON_TYPE_REF = new TypeReference<Map<String, Object>>() {
    };

    /**
     * Path to Scalpel's JSON report file
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/target/scalpel-report.json", property = "cq.scalpelReportJson")
    Path scalpelReportJson;

    /**
     * Path to test-categories.yaml file
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/tooling/scripts/test-categories.yaml", property = "cq.testCategoriesFile")
    Path testCategoriesFile;

    /**
     * Path to write the filtered matrix JSON output
     */
    @Parameter(defaultValue = "${maven.multiModuleProjectDirectory}/target/filtered-matrix.json", property = "cq.outputFile")
    Path outputFile;

    /**
     * Force full build regardless of Scalpel report
     */
    @Parameter(property = "cq.fullBuild", defaultValue = "false")
    boolean fullBuild;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // Check if Scalpel report exists
            if (!Files.exists(scalpelReportJson)) {
                getLog().warn("Scalpel report not found at: " + scalpelReportJson);
                writeFullBuildMatrix();
                return;
            }

            // Read Scalpel report
            Map<String, Object> scalpelReport = readScalpelReport();

            // Check if Scalpel triggered a full build
            Boolean scalpelFullBuild = (Boolean) scalpelReport.get("fullBuildTriggered");
            if (fullBuild || Boolean.TRUE.equals(scalpelFullBuild)) {
                getLog().info("Full build triggered (fullBuild=" + fullBuild +
                        ", scalpelFullBuildTriggered=" + scalpelFullBuild + ")");
                writeFullBuildMatrix();
                return;
            }

            // Get affected modules from Scalpel
            List<Map<String, Object>> affectedModules = (List<Map<String, Object>>) scalpelReport.get("affectedModules");

            if (affectedModules == null || affectedModules.isEmpty()) {
                getLog().info("No affected modules detected - running full build for safety");
                writeFullBuildMatrix();
                return;
            }

            // Extract affected integration test paths
            Set<String> affectedTests = extractAffectedTests(affectedModules);

            if (affectedTests.isEmpty()) {
                getLog().info("No integration tests affected");
                writeEmptyMatrix();
                return;
            }

            // Write filtered matrix with module list
            writeFilteredMatrix(affectedTests);

        } catch (Exception e) {
            getLog().error("Failed to filter test categories", e);
            throw new MojoExecutionException("Failed to filter test categories", e);
        }
    }

    private Map<String, Object> readScalpelReport() throws IOException {
        ObjectMapper jsonMapper = new ObjectMapper();
        Map<String, Object> report = jsonMapper.readValue(scalpelReportJson.toFile(), JSON_TYPE_REF);
        getLog().info("Read Scalpel report from: " + scalpelReportJson);
        return report;
    }

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
                    getLog().info("Extension change detected: " + path);
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
                    getLog().info("Including test (direct change): " + path);
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

        getLog().info("Found " + affectedTests.size() + " affected integration tests");
        return affectedTests;
    }

    private void writeFilteredMatrix(Set<String> affectedTests) throws IOException {
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("modules", new ArrayList<>(affectedTests));
        matrix.put("fullBuild", false);
        matrix.put("totalModules", affectedTests.size());

        writeMatrixJson(matrix);

        getLog().info("Filtered matrix: " + affectedTests.size() + " affected modules");
        getLog().info("Modules: " + affectedTests);
    }

    private void writeFullBuildMatrix() throws IOException {
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("modules", new ArrayList<>());
        matrix.put("fullBuild", true);
        matrix.put("totalModules", 0);

        writeMatrixJson(matrix);
        getLog().info("Full build matrix written");
    }

    private void writeEmptyMatrix() throws IOException {
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("modules", new ArrayList<>());
        matrix.put("fullBuild", false);
        matrix.put("totalModules", 0);

        writeMatrixJson(matrix);
        getLog().info("Empty matrix written (no tests affected)");
    }

    private void writeMatrixJson(Map<String, Object> matrix) throws IOException {
        // Ensure output directory exists
        Files.createDirectories(outputFile.getParent());

        // Write JSON
        ObjectMapper jsonMapper = new ObjectMapper();
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matrix);
        Files.write(outputFile, json.getBytes(StandardCharsets.UTF_8));

        getLog().debug("Written matrix to: " + outputFile);
    }
}
