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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Filters test categories based on Scalpel impact analysis report.
 * <p>
 * This mojo reads a Scalpel JSON report and filters the test-categories.yaml
 * to only include modules that are affected by changes. This enables incremental
 * builds in CI/CD pipelines.
 * <p>
 * Usage:
 *
 * <pre>
 * # Filter a specific category
 * mvn cq:filter-test-categories -Dcq.category=group-01 -N
 *
 * # Specify custom report location
 * mvn cq:filter-test-categories \
 *   -Dcq.category=group-01 \
 *   -Dcq.scalpelReportFile=target/scalpel-report.json \
 *   -N
 *
 * # Output in comma-separated format
 * mvn cq:filter-test-categories \
 *   -Dcq.category=group-01 \
 *   -Dcq.outputFormat=comma \
 *   -N
 *
 * # Output in Maven -pl format
 * mvn cq:filter-test-categories \
 *   -Dcq.category=group-01 \
 *   -Dcq.outputFormat=maven \
 *   -N
 * </pre>
 */
@Mojo(name = "filter-test-categories", requiresProject = false, threadSafe = true)
public class FilterTestCategoriesMojo extends AbstractMojo {

    private static final TypeReference<LinkedHashMap<String, List<String>>> YAML_TYPE_REF = new TypeReference<LinkedHashMap<String, List<String>>>() {
    };

    /**
     * Path to the Scalpel JSON report file
     */
    @Parameter(property = "cq.scalpelReportFile", defaultValue = "${project.build.directory}/scalpel-report.json")
    private File scalpelReportFile;

    /**
     * Path to test-categories.yaml file
     */
    @Parameter(property = "cq.testCategoriesFile", defaultValue = "${maven.multiModuleProjectDirectory}/tooling/scripts/test-categories.yaml")
    private File testCategoriesFile;

    /**
     * Category name to filter (e.g., "group-01")
     */
    @Parameter(property = "cq.category", required = true)
    private String category;

    /**
     * Output file for filtered module list
     */
    @Parameter(property = "cq.outputFile", defaultValue = "${project.build.directory}/filtered-modules.txt")
    private File outputFile;

    /**
     * Output format: "list" (one per line), "comma" (comma-separated), or "maven" (-pl format with integration-tests/ prefix)
     */
    @Parameter(property = "cq.outputFormat", defaultValue = "list")
    private String outputFormat;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // Validate inputs
            if (!testCategoriesFile.exists()) {
                throw new MojoExecutionException("Test categories file not found: " + testCategoriesFile);
            }

            // Read Scalpel report
            ScalpelReport report = readScalpelReport();

            // Check if full build triggered
            if (report.fullBuildTriggered) {
                getLog().info("Full build triggered, including all modules in category " + category);
                writeAllModules();
                return;
            }

            // Extract impacted test modules
            Set<String> impactedModules = extractImpactedTestModules(report);
            getLog().info("Found " + impactedModules.size() + " impacted test modules");

            // Load test categories
            Map<String, List<String>> categories = loadTestCategories();

            // Filter category
            List<String> filteredModules = filterCategory(categories, impactedModules);

            // Write output
            writeOutput(filteredModules);

            getLog().info(String.format("Filtered %d modules for category %s (output: %s)",
                    filteredModules.size(), category, outputFile));

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to filter test categories", e);
        }
    }

    private ScalpelReport readScalpelReport() throws IOException, MojoExecutionException {
        if (!scalpelReportFile.exists()) {
            throw new MojoExecutionException("Scalpel report file not found: " + scalpelReportFile
                    + ". Run 'mvn validate -Dscalpel.mode=report' first.");
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(scalpelReportFile, ScalpelReport.class);
    }

    private Set<String> extractImpactedTestModules(ScalpelReport report) {
        if (report.affectedModules == null) {
            return Set.of();
        }

        return report.affectedModules.stream()
                .map(module -> module.path)
                .filter(path -> path.startsWith("integration-tests/") || path.startsWith("integration-test-groups/"))
                .map(this::normalizeModulePath)
                .collect(Collectors.toSet());
    }

    private String normalizeModulePath(String path) {
        // integration-tests/kafka -> kafka
        // integration-test-groups/foundation/core -> core
        if (path.startsWith("integration-tests/")) {
            return path.substring("integration-tests/".length());
        } else if (path.startsWith("integration-test-groups/")) {
            String[] parts = path.split("/");
            return parts[parts.length - 1];
        }
        return path;
    }

    private Map<String, List<String>> loadTestCategories() throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        return yamlMapper.readValue(testCategoriesFile, YAML_TYPE_REF);
    }

    private List<String> filterCategory(Map<String, List<String>> categories, Set<String> impactedModules) {
        List<String> categoryModules = categories.get(category);
        if (categoryModules == null) {
            getLog().warn("Category '" + category + "' not found in test-categories.yaml");
            return List.of();
        }

        // Filter to only impacted modules
        return categoryModules.stream()
                .filter(impactedModules::contains)
                .collect(Collectors.toList());
    }

    private void writeAllModules() throws IOException, MojoExecutionException {
        Map<String, List<String>> categories = loadTestCategories();
        List<String> categoryModules = categories.get(category);

        if (categoryModules == null) {
            throw new MojoExecutionException("Category '" + category + "' not found in test-categories.yaml");
        }

        writeOutput(categoryModules);
    }

    private void writeOutput(List<String> modules) throws IOException {
        // Ensure output directory exists
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        String content = switch (outputFormat.toLowerCase()) {
            case "comma" -> String.join(",", modules);
            case "maven" -> modules.stream()
                    .map(m -> "integration-tests/" + m)
                    .collect(Collectors.joining(","));
            default -> String.join("\n", modules) + (modules.isEmpty() ? "" : "\n");
        };

        Files.writeString(outputFile.toPath(), content, StandardCharsets.UTF_8);
    }

    // Inner classes for JSON deserialization
    static class ScalpelReport {
        public String version;
        public String scalpelVersion;
        public String baseBranch;
        public boolean fullBuildTriggered;
        public String triggerFile;
        public List<String> changedFiles;
        public List<String> changedProperties;
        public List<String> changedManagedDependencies;
        public List<String> changedManagedPlugins;
        public List<AffectedModule> affectedModules;
    }

    static class AffectedModule {
        public String groupId;
        public String artifactId;
        public String path;
        public List<String> reasons;
        public String category;
        public String sourceSet;
    }
}
