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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Detects changed modules based on git diff and analyzes transitive dependencies
 * to determine which modules need to be built and tested in CI.
 *
 * @since 3.32.0
 */
@Mojo(name = "detect-changed-modules", threadSafe = true, aggregator = true)
public class DetectChangedModulesMojo extends AbstractMojo {

    /**
     * The base commit SHA to compare against. If not provided, will try to resolve from baseBranch.
     */
    @Parameter(property = "cq.baseCommit")
    private String baseCommit;

    /**
     * The base branch to compare against (e.g., 'main', 'origin/main').
     * Only used if baseCommit is not provided.
     */
    @Parameter(property = "cq.baseBranch")
    private String baseBranch;

    /**
     * The output file where the JSON result will be written.
     */
    @Parameter(property = "cq.outputFile", defaultValue = "${project.build.directory}/changed-modules.json")
    private File outputFile;

    /**
     * Whether to include transitive dependents (modules that depend on changed modules).
     */
    @Parameter(property = "cq.includeTransitiveDependents", defaultValue = "true")
    private boolean includeTransitiveDependents;

    /**
     * Path to the test categories YAML file.
     */
    @Parameter(property = "cq.testCategoriesFile", defaultValue = "tooling/scripts/test-categories.yaml")
    private File testCategoriesFile;

    /**
     * Patterns for core modules that trigger a full build when changed.
     */
    @Parameter(property = "cq.coreModulePatterns")
    private List<String> coreModulePatterns;

    /**
     * Force a full build regardless of changes detected.
     */
    @Parameter(property = "cq.forceFullBuild", defaultValue = "${env.FORCE_FULL_BUILD}")
    private String forceFullBuild;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private ProjectBuilder projectBuilder;

    private static final List<String> DEFAULT_CORE_PATTERNS = Arrays.asList(
            "poms/",
            "tooling/",
            "extensions-core/",
            "test-framework/",
            ".github/workflows/",
            "pom.xml");

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // Check if full build is forced
            if ("true".equalsIgnoreCase(forceFullBuild)) {
                getLog().info("Full build forced via FORCE_FULL_BUILD environment variable");
                writeFullBuildResult();
                return;
            }

            // Detect changed files using JGit
            Set<String> changedFiles = detectChangedFilesWithJGit();
            getLog().info("Detected " + changedFiles.size() + " changed files");

            if (changedFiles.isEmpty()) {
                getLog().info("No changes detected - generating empty build matrix");
                writeEmptyBuildResult();
                return;
            }

            // Check if core modules are affected
            if (isCoreModuleAffected(changedFiles)) {
                getLog().info("Core modules affected - triggering full build");
                writeFullBuildResult();
                return;
            }

            // Map changed files to modules
            Set<String> changedModules = mapFilesToModules(changedFiles);
            getLog().info("Changed modules: " + changedModules);

            // Analyze dependencies if requested
            Set<String> affectedModules = changedModules;
            if (includeTransitiveDependents) {
                affectedModules = analyzeTransitiveDependents(changedModules);
                getLog().info("Affected modules (including dependents): " + affectedModules);
            }

            // Generate output
            generateOutput(affectedModules);

        } catch (Exception e) {
            getLog().error("Error detecting changed modules, falling back to full build", e);
            try {
                writeFullBuildResult();
            } catch (IOException ioException) {
                throw new MojoExecutionException("Failed to write fallback result", ioException);
            }
        }
    }

    private String determineBaseBranch() {
        if (baseBranch != null && !baseBranch.trim().isEmpty()) {
            return baseBranch.trim();
        }
        return "origin/main";
    }

    private Set<String> detectChangedFilesWithJGit() throws IOException, GitAPIException {
        Set<String> changedFiles = new HashSet<>();
        File gitDir = findGitDirectory(project.getBasedir());

        if (gitDir == null) {
            getLog().warn("Not a git repository, falling back to full build");
            throw new IOException("Not a git repository");
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
                Git git = new Git(repository)) {

            // Resolve the base commit
            ObjectId baseCommitId;
            if (baseCommit != null && !baseCommit.trim().isEmpty()) {
                // Use provided commit SHA directly
                getLog().info("Using base commit SHA: " + baseCommit);
                baseCommitId = repository.resolve(baseCommit.trim());
                if (baseCommitId == null) {
                    getLog().warn("Could not resolve base commit: " + baseCommit);
                    throw new IOException("Could not resolve base commit: " + baseCommit);
                }
            } else if (baseBranch != null && !baseBranch.trim().isEmpty() && !baseBranch.startsWith("${")) {
                // Fall back to branch resolution (skip if it's an unresolved property placeholder)
                String effectiveBaseBranch = baseBranch.trim();
                getLog().info("Comparing changes against branch: " + effectiveBaseBranch);

                baseCommitId = resolveCommit(repository, effectiveBaseBranch);
                if (baseCommitId == null) {
                    // Try default branch as final fallback
                    getLog().warn("Could not resolve base branch: " + effectiveBaseBranch + ", trying HEAD~1");
                    baseCommitId = repository.resolve("HEAD~1");
                    if (baseCommitId == null) {
                        throw new IOException("Could not resolve base commit from branch or HEAD~1");
                    }
                    getLog().info("Using HEAD~1 as base commit");
                }
            } else {
                // No base specified, use HEAD~1
                getLog().info("No base commit or branch specified, using HEAD~1");
                baseCommitId = repository.resolve("HEAD~1");
                if (baseCommitId == null) {
                    // Single commit repo, use HEAD
                    getLog().info("HEAD~1 not available, using HEAD (likely first commit)");
                    baseCommitId = repository.resolve("HEAD");
                }
            }

            // Get HEAD commit
            ObjectId headCommit = repository.resolve("HEAD");
            if (headCommit == null) {
                getLog().warn("Could not resolve HEAD");
                throw new IOException("Could not resolve HEAD");
            }

            // Find merge base
            ObjectId mergeBase = findMergeBase(repository, baseCommitId, headCommit);
            if (mergeBase == null) {
                getLog().warn("Could not find merge base, using base commit directly");
                mergeBase = baseCommitId;
            }

            getLog().info("Comparing " + mergeBase.getName() + " (merge-base) with " + headCommit.getName() + " (HEAD)");

            // Get diff between merge base and HEAD
            try (ObjectReader reader = repository.newObjectReader()) {
                AbstractTreeIterator oldTreeIterator = prepareTreeParser(repository, mergeBase);
                AbstractTreeIterator newTreeIterator = prepareTreeParser(repository, headCommit);

                List<DiffEntry> diffs = git.diff()
                        .setOldTree(oldTreeIterator)
                        .setNewTree(newTreeIterator)
                        .call();

                for (DiffEntry diff : diffs) {
                    String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? diff.getOldPath()
                            : diff.getNewPath();
                    changedFiles.add(path);
                    System.out.println("====> " + path);
                }
            }
        }

        return changedFiles;
    }

    private File findGitDirectory(File directory) {
        File current = directory;
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return gitDir;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private ObjectId resolveCommit(Repository repository, String ref) throws IOException {
        try {
            return repository.resolve(ref);
        } catch (Exception e) {
            getLog().debug("Could not resolve ref: " + ref, e);
            return null;
        }
    }

    private ObjectId findMergeBase(Repository repository, ObjectId commit1, ObjectId commit2) {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit rev1 = walk.parseCommit(commit1);
            RevCommit rev2 = walk.parseCommit(commit2);

            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(rev1);
            walk.markStart(rev2);

            RevCommit mergeBase = walk.next();
            return mergeBase != null ? mergeBase.getId() : null;
        } catch (Exception e) {
            getLog().debug("Could not find merge base", e);
            return null;
        }
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }

    private boolean isCoreModuleAffected(Set<String> changedFiles) {
        List<String> patterns = coreModulePatterns != null ? coreModulePatterns : DEFAULT_CORE_PATTERNS;

        for (String file : changedFiles) {
            for (String pattern : patterns) {
                if (file.startsWith(pattern) || file.equals(pattern.replace("/", ""))) {
                    getLog().info("Core module pattern matched: " + pattern + " for file: " + file);
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> mapFilesToModules(Set<String> changedFiles) {
        Set<String> modules = new LinkedHashSet<>();
        Path basePath = project.getBasedir().toPath();

        for (String file : changedFiles) {
            Path filePath = basePath.resolve(file);
            String module = findModuleForFile(filePath, basePath);
            if (module != null) {
                modules.add(module);
            }
        }

        return modules;
    }

    private String findModuleForFile(Path filePath, Path basePath) {
        Path currentPath = filePath.getParent();

        while (currentPath != null && currentPath.startsWith(basePath)) {
            Path pomPath = currentPath.resolve("pom.xml");
            if (Files.exists(pomPath)) {
                // Return relative path from base
                String relativePath = basePath.relativize(currentPath).toString();
                if (!relativePath.isEmpty()) {
                    return relativePath.replace('\\', '/');
                }
                break;
            }
            currentPath = currentPath.getParent();
        }

        return null;
    }

    private Set<String> analyzeTransitiveDependents(Set<String> changedModules) {
        Set<String> allAffected = new LinkedHashSet<>(changedModules);
        Map<String, Set<String>> dependencyGraph = buildDependencyGraph();

        // Find all modules that depend on changed modules (transitive)
        Set<String> toProcess = new LinkedHashSet<>(changedModules);
        Set<String> processed = new HashSet<>();

        while (!toProcess.isEmpty()) {
            String module = toProcess.iterator().next();
            toProcess.remove(module);
            processed.add(module);

            // Find modules that depend on this module
            for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
                if (entry.getValue().contains(module) && !processed.contains(entry.getKey())) {
                    allAffected.add(entry.getKey());
                    toProcess.add(entry.getKey());
                }
            }
        }

        return allAffected;
    }

    private Map<String, Set<String>> buildDependencyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();

        for (MavenProject reactorProject : session.getProjects()) {
            String modulePath = getRelativeModulePath(reactorProject);
            Set<String> dependencies = new HashSet<>();

            reactorProject.getDependencies().forEach(dep -> {
                // Find if this dependency is a reactor project
                for (MavenProject otherProject : session.getProjects()) {
                    if (otherProject.getGroupId().equals(dep.getGroupId())
                            && otherProject.getArtifactId().equals(dep.getArtifactId())) {
                        dependencies.add(getRelativeModulePath(otherProject));
                        break;
                    }
                }
            });

            if (modulePath != null) {
                graph.put(modulePath, dependencies);
            }
        }

        return graph;
    }

    private String getRelativeModulePath(MavenProject mavenProject) {
        Path basePath = project.getBasedir().toPath();
        Path projectPath = mavenProject.getBasedir().toPath();

        if (projectPath.equals(basePath)) {
            return null; // Root project
        }

        return basePath.relativize(projectPath).toString().replace('\\', '/');
    }

    private Set<String> getAllModulesInDirectory(String directoryPrefix) {
        Set<String> modules = new LinkedHashSet<>();

        // Remove trailing slash if present
        String dirPath = directoryPrefix.endsWith("/") ? directoryPrefix.substring(0, directoryPrefix.length() - 1)
                : directoryPrefix;
        Path directory = project.getBasedir().toPath().resolve(dirPath);

        if (Files.exists(directory) && Files.isDirectory(directory)) {
            try {
                Files.list(directory)
                        .filter(Files::isDirectory)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(path -> !path.getFileName().toString().equals("target"))
                        .filter(path -> Files.exists(path.resolve("pom.xml")))
                        .map(path -> path.getFileName().toString())
                        .forEach(modules::add);
            } catch (IOException e) {
                getLog().warn("Could not list modules in directory: " + directory, e);
            }
        } else {
            getLog().warn("Directory does not exist: " + directory);
        }

        return modules;
    }

    private void generateOutput(Set<String> affectedModules) throws IOException {
        generateOutput(affectedModules, false);
    }

    private void generateOutput(Set<String> affectedModules, boolean isFullBuild) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("full-build", isFullBuild);
        result.put("changed-modules", new ArrayList<>(affectedModules));

        // Categorize modules
        Map<String, Set<String>> categorizedModules = categorizeModules(affectedModules);
        result.put("native-tests", generateNativeTestsMatrix(categorizedModules.get("integration-tests"), isFullBuild));
        result.put("functional-extension-tests", generateFunctionalTestsConfig(categorizedModules, isFullBuild));
        result.put("extensions-jvm-tests",
                generateJvmTestsConfig(categorizedModules.get("integration-tests-jvm"), isFullBuild));
        result.put("integration-tests-alternative-jdk",
                generateAlternativeJdkConfig(categorizedModules.get("integration-tests"), isFullBuild));

        writeJsonResult(result);
    }

    private Map<String, Set<String>> categorizeModules(Set<String> modules) {
        Map<String, Set<String>> categorized = new HashMap<>();
        categorized.put("extensions", new LinkedHashSet<>());
        categorized.put("extensions-core", new LinkedHashSet<>());
        categorized.put("extensions-jvm", new LinkedHashSet<>());
        categorized.put("integration-tests", new LinkedHashSet<>());
        categorized.put("integration-tests-jvm", new LinkedHashSet<>());
        categorized.put("test-framework", new LinkedHashSet<>());
        categorized.put("tooling", new LinkedHashSet<>());
        categorized.put("catalog", new LinkedHashSet<>());
        categorized.put("other", new LinkedHashSet<>());

        for (String module : modules) {
            if (module.startsWith("extensions-core/")) {
                categorized.get("extensions-core").add(module);
            } else if (module.startsWith("extensions-jvm/")) {
                categorized.get("extensions-jvm").add(module);
            } else if (module.startsWith("extensions/")) {
                categorized.get("extensions").add(module);
            } else if (module.startsWith("integration-tests-jvm/")) {
                categorized.get("integration-tests-jvm").add(module.substring("integration-tests-jvm/".length()));
            } else if (module.startsWith("integration-tests/")) {
                categorized.get("integration-tests").add(module.substring("integration-tests/".length()));
            } else if (module.startsWith("test-framework/")) {
                categorized.get("test-framework").add(module);
            } else if (module.startsWith("tooling/")) {
                categorized.get("tooling").add(module);
            } else if (module.startsWith("catalog/")) {
                categorized.get("catalog").add(module);
            } else {
                categorized.get("other").add(module);
            }
        }

        return categorized;
    }

    private Map<String, Object> generateNativeTestsMatrix(Set<String> integrationTestModules, boolean isFullBuild)
            throws IOException {
        Map<String, Object> matrix = new LinkedHashMap<>();

        // Load test categories
        Map<String, List<String>> categories = loadTestCategories();

        if (isFullBuild) {
            // Full build - include ALL categories with ALL their modules
            List<Map<String, Object>> include = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", entry.getKey());
                item.put("modules", new ArrayList<>(entry.getValue()));
                include.add(item);
            }
            matrix.put("include", include);
            return matrix;
        }

        // Incremental build
        if (integrationTestModules.isEmpty()) {
            matrix.put("include", Collections.emptyList());
            return matrix;
        }

        // Find which categories contain affected modules
        Map<String, Set<String>> affectedModulesByCategory = new LinkedHashMap<>();

        // Group affected modules by their category
        for (String module : integrationTestModules) {
            for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                if (entry.getValue().contains(module)) {
                    affectedModulesByCategory
                            .computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                            .add(module);
                }
            }
        }

        // Generate matrix with category and specific modules
        List<Map<String, Object>> include = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : affectedModulesByCategory.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", entry.getKey());
            item.put("modules", new ArrayList<>(entry.getValue()));
            include.add(item);
        }

        matrix.put("include", include);
        return matrix;
    }

    private Map<String, Object> generateFunctionalTestsConfig(Map<String, Set<String>> categorizedModules,
            boolean isFullBuild) {
        Map<String, Object> config = new LinkedHashMap<>();
        Set<String> topLevelModules = new LinkedHashSet<>();

        if (isFullBuild) {
            // Full build - include ALL functional test directories
            topLevelModules.add("extensions-core");
            topLevelModules.add("extensions");
            topLevelModules.add("test-framework");
            topLevelModules.add("tooling");
            topLevelModules.add("catalog");
        } else {
            // Incremental build - only include affected directories
            if (!categorizedModules.get("extensions-core").isEmpty()) {
                topLevelModules.add("extensions-core");
            }
            if (!categorizedModules.get("extensions").isEmpty()) {
                topLevelModules.add("extensions");
            }
            if (!categorizedModules.get("test-framework").isEmpty()) {
                topLevelModules.add("test-framework");
            }
            if (!categorizedModules.get("tooling").isEmpty()) {
                topLevelModules.add("tooling");
            }
            if (!categorizedModules.get("catalog").isEmpty()) {
                topLevelModules.add("catalog");
            }
        }

        List<Map<String, Object>> include = new ArrayList<>();
        if (!topLevelModules.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", "");
            item.put("modules", new ArrayList<>(topLevelModules));
            include.add(item);
        }

        config.put("include", include);
        return config;
    }

    private Map<String, Object> generateJvmTestsConfig(Set<String> jvmTestModules, boolean isFullBuild) {
        Map<String, Object> config = new LinkedHashMap<>();
        List<Map<String, Object>> include = new ArrayList<>();

        Set<String> modulesToTest = jvmTestModules;
        if (isFullBuild) {
            // Full build - get ALL modules from integration-tests-jvm
            modulesToTest = getAllModulesInDirectory("integration-tests-jvm/");
        }

        if (!modulesToTest.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", "");
            item.put("modules", new ArrayList<>(modulesToTest));
            include.add(item);
        }

        config.put("include", include);
        return config;
    }

    private Map<String, Object> generateAlternativeJdkConfig(Set<String> integrationTestModules, boolean isFullBuild) {
        Map<String, Object> config = new LinkedHashMap<>();
        List<Map<String, Object>> include = new ArrayList<>();

        Set<String> modulesToTest = integrationTestModules;
        if (isFullBuild) {
            // Full build - get ALL modules from integration-tests
            modulesToTest = getAllModulesInDirectory("integration-tests/");
        }

        if (modulesToTest.isEmpty()) {
            config.put("include", include);
            return config;
        }

        // Split modules into two groups for parallel execution
        List<String> moduleList = new ArrayList<>(modulesToTest);
        int midpoint = moduleList.size() / 2;

        List<String> group1Modules = moduleList.subList(0, midpoint);
        List<String> group2Modules = moduleList.subList(midpoint, moduleList.size());

        if (!group1Modules.isEmpty()) {
            Map<String, Object> group1 = new LinkedHashMap<>();
            group1.put("category", "group-01");
            group1.put("modules", new ArrayList<>(group1Modules));
            include.add(group1);
        }

        if (!group2Modules.isEmpty()) {
            Map<String, Object> group2 = new LinkedHashMap<>();
            group2.put("category", "group-02");
            group2.put("modules", new ArrayList<>(group2Modules));
            include.add(group2);
        }

        config.put("include", include);
        return config;
    }

    private Map<String, List<String>> loadTestCategories() throws IOException {
        Map<String, List<String>> categories = new LinkedHashMap<>();

        if (!testCategoriesFile.exists()) {
            getLog().warn("Test categories file not found: " + testCategoriesFile);
            return categories;
        }

        // Simple YAML parser for the test-categories.yaml structure
        List<String> lines = Files.readAllLines(testCategoriesFile.toPath(), StandardCharsets.UTF_8);
        String currentCategory = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.endsWith(":")) {
                // Category line
                currentCategory = line.substring(0, line.length() - 1);
                categories.put(currentCategory, new ArrayList<>());
            } else if (line.startsWith("-") && currentCategory != null) {
                // Module line
                String module = line.substring(1).trim();
                categories.get(currentCategory).add(module);
            }
        }

        return categories;
    }

    private void writeFullBuildResult() throws IOException {
        // For full build, generate output with ALL modules
        generateOutput(Collections.emptySet(), true);
    }

    private void writeEmptyBuildResult() throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("full-build", false);
        result.put("changed-modules", Collections.emptyList());
        result.put("native-tests", Collections.singletonMap("include", Collections.emptyList()));
        result.put("functional-extension-tests", Collections.singletonMap("include", Collections.emptyList()));
        result.put("extensions-jvm-tests", Collections.singletonMap("include", Collections.emptyList()));
        result.put("integration-tests-alternative-jdk", Collections.singletonMap("include", Collections.emptyList()));
        writeJsonResult(result);
    }

    private void writeJsonResult(Map<String, Object> result) throws IOException {
        outputFile.getParentFile().mkdirs();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        mapper.writeValue(outputFile, result);
        getLog().info("Written result to: " + outputFile.getAbsolutePath());
        getLog().info("Result: " + mapper.writeValueAsString(result));
    }
}

// Made with Bob
