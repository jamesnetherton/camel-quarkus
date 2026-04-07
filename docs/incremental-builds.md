# Incremental Builds in CI

## Overview

The Camel Quarkus CI workflow now supports incremental builds, which significantly reduces build times for pull requests by only building and testing modules that are affected by the changes.

## How It Works

### 1. Change Detection

When a PR is created or updated, the `camel-quarkus:detect-changed-modules` Maven mojo:
- Compares the PR branch against the target branch (e.g., `main`) using JGit
- Identifies all changed files by computing the diff between merge-base and HEAD
- Maps changed files to their corresponding Maven modules
- Analyzes transitive dependencies to find all affected modules

**Cross-Platform**: The mojo uses Eclipse JGit (pure Java Git implementation), making it fully cross-platform without requiring git CLI to be installed.

### 2. Dependency Analysis

The mojo performs transitive dependency analysis:
- If module A changes, module A is marked for building
- Any module that depends on A (directly or transitively) is also marked
- This ensures that all potentially affected code is tested

### 3. Dynamic Test Matrix

Based on the affected modules, the workflow generates a dynamic test matrix:
- **Native Tests**: Only runs test categories containing affected integration tests
- **Functional Tests**: Only tests affected extensions, core modules, or tooling
- **JVM Tests**: Only runs affected JVM-only integration tests
- **Alternative JDK Tests**: Only tests affected modules on JDK 21

### 4. Fallback to Full Build

A full build is automatically triggered when:
- Core modules are changed (`poms/`, `tooling/`, `extensions-core/`, `test-framework/`)
- Workflow files are modified (`.github/workflows/`)
- Root `pom.xml` is changed
- Change detection fails or encounters errors
- `FORCE_FULL_BUILD=true` environment variable is set

## Module Categories

### Core Modules (Always Trigger Full Build)
- `poms/**` - Build parent, BOM, dependency management
- `tooling/**` - Maven plugins, build scripts
- `extensions-core/**` - Core Quarkus extensions
- `test-framework/**` - Test framework modules
- `.github/workflows/**` - CI workflow definitions
- `pom.xml` - Root project descriptor

### Extension Modules (Incremental Build)
- `extensions/**` - Camel component extensions
- `extensions-jvm/**` - JVM-only extensions

### Integration Test Modules (Incremental Build)
- `integration-tests/**` - Native-capable integration tests
- `integration-tests-jvm/**` - JVM-only integration tests

## Configuration

### Maven Mojo Parameters

The `camel-quarkus:detect-changed-modules` mojo accepts the following parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `baseBranch` | `${env.GITHUB_BASE_REF}` or `origin/main` | Target branch for comparison |
| `outputFile` | `${project.build.directory}/changed-modules.json` | Output file path |
| `includeTransitiveDependents` | `true` | Include modules that depend on changed modules |
| `testCategoriesFile` | `tooling/scripts/test-categories.yaml` | Path to test categories file |
| `coreModulePatterns` | See defaults | Patterns for core modules |
| `forceFullBuild` | `${env.FORCE_FULL_BUILD}` | Force full build regardless of changes |

### Example Usage

```bash
# Detect changed modules for a PR
./mvnw camel-quarkus:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# Force a full build
FORCE_FULL_BUILD=true ./mvnw camel-quarkus:detect-changed-modules
```

## Output Format

The mojo generates a JSON file with the following structure:

```json
{
  "full-build": false,
  "changed-modules": [
    "extensions/kafka",
    "integration-tests/kafka",
    "integration-tests/kafka-sasl"
  ],
  "native-tests": {
    "include": [
      {"category": "group-01"},
      {"category": "group-03"}
    ]
  },
  "functional-extension-tests": {
    "modules": ["extensions/kafka"]
  },
  "extensions-jvm-tests": {
    "modules": []
  },
  "integration-tests-alternative-jdk": {
    "modules": ["kafka", "kafka-sasl"]
  }
}
```

## Benefits

### Time Savings
- **Typical Extension PR**: 50-70% reduction in CI time
- **Documentation-only PR**: Skips all tests (except validation)
- **Core Module PR**: Full build (no time savings, but ensures safety)

### Resource Efficiency
- Reduced GitHub Actions minutes consumption
- Lower infrastructure costs
- Faster feedback for developers

### Maintained Coverage
- Transitive dependency analysis ensures no gaps
- Automatic fallback to full build for risky changes
- All tests still run on main branch merges

## Troubleshooting

### Issue: Incremental build missed a dependency

**Solution**: This shouldn't happen due to transitive analysis, but if it does:
1. Check if the dependency is properly declared in `pom.xml`
2. Verify the module is in the reactor build
3. Force a full build: `FORCE_FULL_BUILD=true`

### Issue: Change detection fails

**Symptom**: Workflow falls back to full build with error message

**Common Causes**:
- Git fetch failed (network issue)
- Base branch doesn't exist locally
- Shallow clone depth insufficient

**Solution**: The workflow automatically falls back to full build for safety

### Issue: Want to force full build for a PR

**Solution**: Add a comment or commit message with `[full-build]` or set the `FORCE_FULL_BUILD` environment variable

## Examples

### Example 1: Single Extension Change

**Changed Files**:
```
extensions/kafka/runtime/src/main/java/KafkaComponent.java
```

**Result**:
- Builds: `extensions/kafka`
- Tests: `integration-tests/kafka`, `integration-tests/kafka-sasl`, `integration-tests/kafka-ssl`
- Skips: All other extensions and tests

### Example 2: Core Module Change

**Changed Files**:
```
extensions-core/core/runtime/src/main/java/CamelQuarkusCore.java
```

**Result**:
- Full build triggered (core module affected)
- All tests run

### Example 3: Documentation-only Change

**Changed Files**:
```
docs/modules/ROOT/pages/user-guide.adoc
README.md
```

**Result**:
- No modules affected
- All test jobs skipped
- Only documentation validation runs

## Future Enhancements

Potential improvements for the incremental build system:

1. **Caching**: Cache test results for unchanged modules
2. **Parallel Optimization**: Better distribution of affected modules across workers
3. **Smart Retries**: Only retry failed modules, not entire categories
4. **Build Time Prediction**: Estimate CI time based on affected modules
5. **PR Labels**: Auto-label PRs based on affected areas

## Technical Implementation

### JGit Integration

The mojo uses Eclipse JGit for all Git operations:
- **Repository Access**: Opens the Git repository using `FileRepositoryBuilder`
- **Fetch Operations**: Fetches remote branches when needed
- **Merge Base**: Computes the merge-base between target branch and HEAD
- **Diff Calculation**: Uses JGit's diff API to find changed files
- **Cross-Platform**: Pure Java implementation works on Windows, Linux, and macOS

### Key JGit Operations

```java
// Open repository
Repository repository = new FileRepositoryBuilder()
    .setGitDir(gitDir)
    .readEnvironment()
    .findGitDir()
    .build();

// Find merge base
ObjectId mergeBase = findMergeBase(repository, baseCommit, headCommit);

// Get diff
List<DiffEntry> diffs = git.diff()
    .setOldTree(oldTreeIterator)
    .setNewTree(newTreeIterator)
    .call();
```

## Contributing

To modify the incremental build logic:

1. **Maven Mojo**: Edit `tooling/maven-plugin/src/main/java/org/apache/camel/quarkus/maven/DetectChangedModulesMojo.java`
   - Uses JGit for all Git operations (no shell commands)
   - Dependency: `org.eclipse.jgit:org.eclipse.jgit`
2. **Workflow Integration**: Edit `.github/workflows/ci-build.yaml`
3. **Test Categories**: Edit `tooling/scripts/test-categories.yaml`

Always test changes thoroughly before merging to ensure the fallback mechanisms work correctly.