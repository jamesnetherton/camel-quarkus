# Incremental Build Implementation Plan

## Overview
Implement incremental builds for Camel Quarkus CI using Scalpel 0.3.3 Maven extension to reduce build times by only testing affected modules in pull requests.

## Key Findings

### Scalpel Report Categories
Scalpel categorizes modules into three types:
- **DIRECT**: Modules with actual code changes (need testing)
- **DOWNSTREAM**: Modules that depend on changed modules (need testing)
- **UPSTREAM**: Dependencies of changed modules (DON'T need testing - not affected by changes)

**Important**: For incremental builds, we only care about DIRECT + DOWNSTREAM modules. UPSTREAM modules are dependencies that haven't changed and don't need retesting.

## Implementation Status

### ✅ Completed
1. Added Scalpel 0.3.3 extension to `.mvn/extensions.xml`
2. Configured Scalpel in `.mvn/maven.config`:
   - Disabled by default (enable with `-Dscalpel.enabled=true`)
   - Auto-detects base branch from `GITHUB_BASE_REF`
   - Disabled on main branches (main, camel-main, quarkus-main, release branches)
   - Full build triggers: `.mvn/**`, `pom.xml`, `poms/**`, `test-categories.yaml`, `ci-build.yaml`
3. Created `FilterTestCategoriesMojo` in `tooling/maven-plugin`
4. Modified `.github/workflows/ci-build.yaml` for Scalpel integration
5. Modified workflow for fork testing (commented out push trigger, added test-base branch, removed repository check, commented out uncommitted changes checks)
6. Built and installed maven plugin to local repository
7. Created and pushed `test-base` branch (infrastructure only)
8. Created and pushed `test-scalpel-incremental-new` branch (infrastructure + test changes)

### 🔧 Issue Identified
The `FilterTestCategoriesMojo` currently includes ALL affected modules from the Scalpel report, including UPSTREAM dependencies. This is inefficient because:
- UPSTREAM modules are dependencies that haven't changed
- They don't need retesting since they're not affected by the changes
- Including them defeats the purpose of incremental builds

**Fix needed**: Modify `FilterTestCategoriesMojo.extractImpactedTestModules()` to filter by category, only including DIRECT and DOWNSTREAM modules.

### 📋 Next Steps
1. **[NEEDS FIX]** Update `FilterTestCategoriesMojo` to exclude UPSTREAM modules:
   ```java
   private Set<String> extractImpactedTestModules(ScalpelReport report) {
       if (report.affectedModules == null) {
           return Set.of();
       }

       return report.affectedModules.stream()
               .filter(module -> "DIRECT".equals(module.category) || "DOWNSTREAM".equals(module.category))
               .map(module -> module.path)
               .filter(path -> path.startsWith("integration-tests/") || path.startsWith("integration-test-groups/"))
               .map(this::normalizeModulePath)
               .collect(Collectors.toSet());
   }
   ```

2. **[USER ACTION]** Rebuild and reinstall maven plugin after fix:
   ```bash
   ./mvnw clean install -pl tooling/maven-plugin
   ```

3. **[USER ACTION]** Create PR: test-scalpel-incremental-new -> test-base
   - URL: https://github.com/jamesnetherton/camel-quarkus/compare/test-base...test-scalpel-incremental-new

4. **[USER ACTION]** Verify incremental build in GitHub Actions:
   - Check for filtered test categories
   - Verify skipped categories
   - Confirm faster build times
   - Verify only DIRECT + DOWNSTREAM modules are tested

5. **[USER ACTION]** Test full build trigger by modifying `pom.xml`

6. **[USER ACTION]** Before submitting to Apache, revert test-specific workflow changes:
   - Uncomment push trigger
   - Remove test-base from pull_request branches
   - Restore repository check
   - Uncomment uncommitted changes checks

7. **[OPTIONAL]** Add user-facing documentation about incremental build feature

## Technical Details

### Scalpel Configuration
- **Mode**: Report only (generates JSON without modifying build)
- **Base Branch**: Auto-detected from `GITHUB_BASE_REF` environment variable
- **Report File**: `target/scalpel-report.json`
- **Impacted Log**: `target/scalpel-impacted.log`
- **Fail Safe**: Enabled (falls back to full build on errors)

### Full Build Triggers
Changes to these paths trigger a full build:
- `.mvn/**` - Maven configuration
- `pom.xml` - Root POM
- `poms/**` - BOM and parent POMs
- `tooling/scripts/test-categories.yaml` - Test category definitions
- `.github/workflows/ci-build.yaml` - CI workflow

### CI Integration
1. Pre-build checks job enables Scalpel with `-Dscalpel.enabled=true`
2. Scalpel analyzes changes and generates report
3. For each test category job:
   - `FilterTestCategoriesMojo` reads Scalpel report
   - Filters test-categories.yaml to only include affected modules
   - Outputs filtered module list
   - CI runs tests only for filtered modules

### Test Branch Setup
- **test-base**: Contains Scalpel infrastructure and workflow modifications for fork testing
- **test-scalpel-incremental-new**: Based on test-base, adds test changes to http extension and kafka integration test

## References
- Scalpel Documentation: https://github.com/maveniverse/scalpel
- Scalpel Maven Extension: `eu.maveniverse.maven.scalpel:extension:0.3.3`
