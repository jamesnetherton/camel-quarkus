# Incremental Build Implementation Plan with Scalpel

## Overview

This document outlines the plan to implement incremental builds in the Camel Quarkus CI pipeline using the Scalpel Maven extension (version 0.3.3). The goal is to reduce CI build times by only building and testing modules affected by changes in pull requests.

## 1. Scalpel Integration Approach

### 1.1 Extension Setup

**File: `.mvn/extensions.xml`** (new file)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>eu.maveniverse.maven.scalpel</groupId>
        <artifactId>extension</artifactId>
        <version>0.3.3</version>
    </extension>
</extensions>
```

### 1.2 Configuration Strategy

**File: `.mvn/maven.config`** (append to existing)

```properties
# Scalpel Configuration for Incremental Builds
-Dscalpel.enabled=true
-Dscalpel.mode=report
-Dscalpel.baseBranch=origin/main
-Dscalpel.fetchBaseBranch=true
-Dscalpel.reportFile=target/scalpel-report.json
-Dscalpel.impactedLog=target/scalpel-impacted.log
-Dscalpel.failSafe=true
-Dscalpel.buildAllIfNoChanges=true
-Dscalpel.disableOnBranch=main,camel-main,quarkus-main,\d+\.\d+\.x
```

**Rationale:**
- **mode=report**: Generate reports without modifying the build (we'll control the build via GitHub Actions)
- **baseBranch=origin/main**: Compare against main branch (auto-detected in PRs via `GITHUB_BASE_REF`)
- **fetchBaseBranch=true**: Handle shallow clones in GitHub Actions
- **failSafe=true**: Fall back to full build on errors
- **buildAllIfNoChanges=true**: For scheduled/cron builds
- **disableOnBranch**: Disable on main branches (always full build on main)

### 1.3 Full Build Triggers

Files/paths that should trigger a full build when changed:

```properties
-Dscalpel.fullBuildTriggers=.mvn/**,pom.xml,poms/**,tooling/scripts/test-categories.yaml,.github/workflows/ci-build.yaml,Jenkinsfile,Jenkinsfile.*
```

**Rationale:**
- `.mvn/**`: Maven configuration changes
- `pom.xml`, `poms/**`: Dependency/version changes affecting all modules
- `test-categories.yaml`: Test grouping changes
- `ci-build.yaml`: CI workflow changes
- `Jenkinsfile*`: Jenkins configuration changes

### 1.4 Path Exclusions

Paths to ignore from change detection:

```properties
-Dscalpel.excludePaths=**.adoc,**.md,KEYS,LICENSE.txt,NOTICE.txt,camel-quarkus-sbom/**,docs/antora.yml,release-utils/**,.github/*.sh,.github/*.yaml,.github/*.yml,.github/workflows/assign-*.yaml,.github/workflows/label-issue.yaml,.github/workflows/pr-validate.yml,.github/workflows/pr-doc-validation.yaml,.github/workflows/*-cron.yaml,.github/workflows/generate-sbom-main.yml,.github/workflows/synchronize-dependabot-branch.yaml
```

**Rationale:** These match the existing `paths-ignore` in ci-build.yaml

## 2. Maven Plugin Enhancement

### 2.1 New Mojo: `filter-test-categories`

**Location:** `tooling/maven-plugin/src/main/java/org/apache/camel/quarkus/maven/FilterTestCategoriesMojo.java`

**Purpose:** Filter test-categories.yaml based on Scalpel report to determine which modules need testing in a specific category.

**Parameters:**
- `scalpelReportFile` (default: `${project.build.directory}/scalpel-report.json`) - Path to Scalpel JSON report
- `testCategoriesFile` (default: `${project.basedir}/tooling/scripts/test-categories.yaml`) - Path to test-categories.yaml
- `category` (required) - Category name to filter (e.g., "group-01")
- `outputFile` (default: `${project.build.directory}/filtered-modules.txt`) - Output file with filtered module list
- `outputFormat` (default: `list`) - Output format: `list` (one per line), `comma` (comma-separated), or `maven` (-pl format)

**Functionality:**
1. Read Scalpel JSON report
2. Extract impacted modules under `integration-tests/` and `integration-test-groups/`
3. Load test-categories.yaml
4. Filter modules in specified category to only those impacted
5. Write filtered list to output file
6. Handle full build scenario (when `fullBuildTriggered=true`)

**Example Usage:**
```bash
./mvnw cq:filter-test-categories \
  -Dcq.category=group-01 \
  -Dcq.scalpelReportFile=target/scalpel-report.json \
  -Dcq.outputFile=target/filtered-group-01.txt \
  -N
```

**Output Examples:**

*List format (default):*
```
caffeine
git
hazelcast
kafka
```

*Comma format:*
```
caffeine,git,hazelcast,kafka
```

*Maven format (-pl):*
```
integration-tests/caffeine,integration-tests/git,integration-tests/hazelcast,integration-tests/kafka
```

### 2.2 Implementation Details

**Key Classes:**

```java
@Mojo(name = "filter-test-categories", requiresProject = false, threadSafe = true)
public class FilterTestCategoriesMojo extends AbstractMojo {
    
    @Parameter(property = "cq.scalpelReportFile", 
               defaultValue = "${project.build.directory}/scalpel-report.json")
    private File scalpelReportFile;
    
    @Parameter(property = "cq.testCategoriesFile",
               defaultValue = "${project.basedir}/tooling/scripts/test-categories.yaml")
    private File testCategoriesFile;
    
    @Parameter(property = "cq.category", required = true)
    private String category;
    
    @Parameter(property = "cq.outputFile",
               defaultValue = "${project.build.directory}/filtered-modules.txt")
    private File outputFile;
    
    @Parameter(property = "cq.outputFormat", defaultValue = "list")
    private String outputFormat; // list, comma, maven
    
    @Override
    public void execute() throws MojoExecutionException {
        // 1. Read Scalpel report
        ScalpelReport report = readScalpelReport();
        
        // 2. Check if full build triggered
        if (report.isFullBuildTriggered()) {
            getLog().info("Full build triggered, including all modules");
            writeAllModules();
            return;
        }
        
        // 3. Extract impacted test modules
        Set<String> impactedModules = extractImpactedTestModules(report);
        
        // 4. Load test categories
        Map<String, List<String>> categories = loadTestCategories();
        
        // 5. Filter category
        List<String> filteredModules = filterCategory(categories, impactedModules);
        
        // 6. Write output
        writeOutput(filteredModules);
        
        getLog().info(String.format("Filtered %d modules for category %s", 
                                    filteredModules.size(), category));
    }
    
    private Set<String> extractImpactedTestModules(ScalpelReport report) {
        return report.getAffectedModules().stream()
            .map(AffectedModule::getPath)
            .filter(path -> path.startsWith("integration-tests/") || 
                           path.startsWith("integration-test-groups/"))
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
}
```

**Dependencies to add to maven-plugin pom.xml:**
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>
```

## 3. GitHub Actions Integration

### 3.1 Scalpel Report Generation

**Modify `initial-mvn-install` job:**

Add a new step after "mvn clean install -DskipTests":

```yaml
- name: Generate Scalpel Impact Report
  if: github.event_name == 'pull_request'
  run: |
    # Generate Scalpel report
    ./mvnw validate -Dscalpel.mode=report \
      -Dscalpel.baseBranch=origin/${{ github.base_ref }} \
      -Dscalpel.fetchBaseBranch=true \
      ${CQ_MAVEN_ARGS}
    
    # Check if full build was triggered
    FULL_BUILD=$(jq -r '.fullBuildTriggered' target/scalpel-report.json)
    echo "full-build=${FULL_BUILD}" >> $GITHUB_OUTPUT
    
    # Log summary
    if [ "$FULL_BUILD" = "true" ]; then
      echo "::notice::Full build triggered"
      TRIGGER_FILE=$(jq -r '.triggerFile' target/scalpel-report.json)
      echo "::notice::Trigger file: ${TRIGGER_FILE}"
    else
      AFFECTED_COUNT=$(jq '.affectedModules | length' target/scalpel-report.json)
      echo "::notice::Incremental build: ${AFFECTED_COUNT} modules affected"
    fi
  id: scalpel-report

- name: Upload Scalpel Reports
  if: github.event_name == 'pull_request'
  uses: actions/upload-artifact@v7
  with:
    name: scalpel-reports
    path: |
      target/scalpel-report.json
      target/scalpel-impacted.log
    retention-days: 1
```

**Add output to job:**
```yaml
outputs:
  matrix: ${{ steps.set-native-matrix.outputs.matrix }}
  examples-matrix: ${{ steps.set-examples-matrix.outputs.examples-matrix }}
  alternate-jvm-matrix: ${{ steps.set-alternate-jvm-matrix.outputs.alternate-jvm-matrix }}
  full-build: ${{ steps.scalpel-report.outputs.full-build || 'true' }}
```

### 3.2 Native Tests Job Modification

**Strategy:**
1. Download Scalpel reports
2. Use Maven mojo to filter test-categories.yaml based on impacted modules
3. Run tests only for affected modules in the category
4. Skip category if no modules are affected

**New steps before "Integration Tests":**

```yaml
- name: Download Scalpel Reports
  if: github.event_name == 'pull_request' && needs.initial-mvn-install.outputs.full-build != 'true'
  uses: actions/download-artifact@v8
  with:
    name: scalpel-reports
    path: target/
  continue-on-error: true

- name: Filter Test Categories
  if: github.event_name == 'pull_request' && needs.initial-mvn-install.outputs.full-build != 'true'
  id: filter-categories
  run: |
    if [ -f target/scalpel-report.json ]; then
      # Use Maven mojo to filter test categories
      ./mvnw cq:filter-test-categories \
        -Dcq.category=${{ matrix.category }} \
        -Dcq.scalpelReportFile=target/scalpel-report.json \
        -Dcq.outputFile=target/filtered-modules.txt \
        -Dcq.outputFormat=list \
        -N ${CQ_MAVEN_ARGS}
      
      if [ -s target/filtered-modules.txt ]; then
        MODULE_COUNT=$(wc -l < target/filtered-modules.txt)
        echo "skip-tests=false" >> $GITHUB_OUTPUT
        echo "::notice::Running tests for ${MODULE_COUNT} modules in category ${{ matrix.category }}"
      else
        echo "skip-tests=true" >> $GITHUB_OUTPUT
        echo "::notice::No impacted modules in category ${{ matrix.category }}, skipping tests"
      fi
    else
      echo "skip-tests=false" >> $GITHUB_OUTPUT
      echo "::warning::Scalpel report not found, running full test suite"
    fi

- name: Integration Tests
  if: steps.filter-categories.outputs.skip-tests != 'true'
  run: |
    # Determine which modules to test
    if [ -f target/filtered-modules.txt ]; then
      # Use filtered modules from Scalpel
      MODULES_FILE=target/filtered-modules.txt
      echo "::notice::Using filtered module list from Scalpel"
    else
      # Use all modules from test-categories.yaml (full build)
      yq -M -N e ".${{ matrix.category }}" tooling/scripts/test-categories.yaml | grep -vE '^\s*#' | cut -f2 -d' ' > target/all-modules.txt
      MODULES_FILE=target/all-modules.txt
      echo "::notice::Using full module list from test-categories.yaml"
    fi
    
    # Separate JVM and native modules
    JVM_MODULES=()
    NATIVE_MODULES=()
    
    for MODULE in $(cat ${MODULES_FILE}); do
      if [[ "${MODULE}" == "null" ]]; then
        continue
      fi

      MODULE="integration-tests/$(echo ${MODULE} | sed 's/^[ \t]*//;s/[ \t]*$//')"

      if [[ "x$(./mvnw org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=ci.native.tests.skip -DforceStdout -q -f ${MODULE})" == "xtrue" ]]; then
        JVM_MODULES+=("${MODULE}")
      else
        NATIVE_MODULES+=("${MODULE}")
      fi
    done

    if [[ ${#JVM_MODULES[@]} -eq 0 ]] && [[ ${#NATIVE_MODULES[@]} -eq 0 ]]; then
      echo "No test modules were found for category ${{ matrix.category }}"
      exit 1
    fi

    IFS=,
    if [[ ${JVM_MODULES[@]} ]]; then
      eval ./mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} clean test \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip \
        -Pdocker,ci \
        -pl "${JVM_MODULES[*]}"
    fi

    if [[ ${NATIVE_MODULES[@]} ]]; then
      eval ./mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} clean verify \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip \
        -Dquarkus.native.builder-image.pull=missing \
        -Pnative,docker,ci \
        --fail-at-end \
        -pl "${NATIVE_MODULES[*]}"
    fi
```

### 3.3 Functional Extension Tests Job Modification

**Strategy:**
1. Download Scalpel reports
2. Extract affected modules under extensions-core/, extensions/, test-framework/, tooling/, catalog/
3. Build only affected modules or skip if no changes

```yaml
- name: Download Scalpel Reports
  if: github.event_name == 'pull_request' && needs.initial-mvn-install.outputs.full-build != 'true'
  uses: actions/download-artifact@v8
  with:
    name: scalpel-reports
    path: target/
  continue-on-error: true

- name: Determine Affected Modules
  id: affected-modules
  run: |
    if [ -f target/scalpel-report.json ]; then
      # Check if full build triggered
      FULL_BUILD=$(jq -r '.fullBuildTriggered' target/scalpel-report.json)
      
      if [ "$FULL_BUILD" = "true" ]; then
        echo "skip-tests=false" >> $GITHUB_OUTPUT
        echo "use-filter=false" >> $GITHUB_OUTPUT
        echo "::notice::Full build triggered, testing all modules"
      else
        # Extract affected modules
        AFFECTED_CORE=$(jq -r '.affectedModules[] | select(.path | startswith("extensions-core/")) | .path | sub("^extensions-core/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        AFFECTED_EXT=$(jq -r '.affectedModules[] | select(.path | startswith("extensions/")) | .path | sub("^extensions/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        AFFECTED_TEST=$(jq -r '.affectedModules[] | select(.path | startswith("test-framework/")) | .path | sub("^test-framework/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        AFFECTED_TOOL=$(jq -r '.affectedModules[] | select(.path | startswith("tooling/")) | .path | sub("^tooling/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        AFFECTED_CAT=$(jq -r '.affectedModules[] | select(.path | startswith("catalog/")) | .path | sub("^catalog/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        
        echo "affected-core=${AFFECTED_CORE}" >> $GITHUB_OUTPUT
        echo "affected-ext=${AFFECTED_EXT}" >> $GITHUB_OUTPUT
        echo "affected-test=${AFFECTED_TEST}" >> $GITHUB_OUTPUT
        echo "affected-tool=${AFFECTED_TOOL}" >> $GITHUB_OUTPUT
        echo "affected-cat=${AFFECTED_CAT}" >> $GITHUB_OUTPUT
        
        if [ -z "$AFFECTED_CORE" ] && [ -z "$AFFECTED_EXT" ] && [ -z "$AFFECTED_TEST" ] && [ -z "$AFFECTED_TOOL" ] && [ -z "$AFFECTED_CAT" ]; then
          echo "skip-tests=true" >> $GITHUB_OUTPUT
          echo "::notice::No affected modules in functional extensions, skipping tests"
        else
          echo "skip-tests=false" >> $GITHUB_OUTPUT
          echo "use-filter=true" >> $GITHUB_OUTPUT
          echo "::notice::Incremental build: testing affected modules only"
        fi
      fi
    else
      echo "skip-tests=false" >> $GITHUB_OUTPUT
      echo "use-filter=false" >> $GITHUB_OUTPUT
      echo "::warning::Scalpel report not found, running full test suite"
    fi

- name: cd extensions-core && mvn test
  if: steps.affected-modules.outputs.skip-tests != 'true'
  run: |
    cd extensions-core
    
    if [ "${{ steps.affected-modules.outputs.use-filter }}" = "true" ] && [ -n "${{ steps.affected-modules.outputs.affected-core }}" ]; then
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -pl "${{ steps.affected-modules.outputs.affected-core }}" -am \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip -Dcamel-quarkus.update-extension-doc-page.skip \
        --fail-at-end \
        test
    elif [ "${{ steps.affected-modules.outputs.use-filter }}" != "true" ]; then
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip -Dcamel-quarkus.update-extension-doc-page.skip \
        --fail-at-end \
        test
    else
      echo "::notice::No affected modules in extensions-core, skipping"
    fi

- name: cd extensions && mvn test
  if: steps.affected-modules.outputs.skip-tests != 'true'
  run: |
    cd extensions
    
    if [ "${{ steps.affected-modules.outputs.use-filter }}" = "true" ] && [ -n "${{ steps.affected-modules.outputs.affected-ext }}" ]; then
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -pl "${{ steps.affected-modules.outputs.affected-ext }}" -am \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip -Dcamel-quarkus.update-extension-doc-page.skip \
        --fail-at-end \
        test
    elif [ "${{ steps.affected-modules.outputs.use-filter }}" != "true" ]; then
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip -Dcamel-quarkus.update-extension-doc-page.skip \
        --fail-at-end \
        test
    else
      echo "::notice::No affected modules in extensions, skipping"
    fi

# Similar pattern for test-framework, tooling, and catalog
```

### 3.4 Integration Tests JVM Job Modification

```yaml
- name: Download Scalpel Reports
  if: github.event_name == 'pull_request' && needs.initial-mvn-install.outputs.full-build != 'true'
  uses: actions/download-artifact@v8
  with:
    name: scalpel-reports
    path: target/
  continue-on-error: true

- name: Determine Affected Modules
  id: affected-modules
  run: |
    if [ -f target/scalpel-report.json ]; then
      FULL_BUILD=$(jq -r '.fullBuildTriggered' target/scalpel-report.json)
      
      if [ "$FULL_BUILD" = "true" ]; then
        echo "skip-tests=false" >> $GITHUB_OUTPUT
        echo "use-filter=false" >> $GITHUB_OUTPUT
      else
        AFFECTED=$(jq -r '.affectedModules[] | select(.path | startswith("integration-tests-jvm/")) | .path | sub("^integration-tests-jvm/"; "")' target/scalpel-report.json | sort -u | tr '\n' ',' | sed 's/,$//')
        
        if [ -z "$AFFECTED" ]; then
          echo "skip-tests=true" >> $GITHUB_OUTPUT
          echo "::notice::No affected modules in integration-tests-jvm, skipping tests"
        else
          echo "skip-tests=false" >> $GITHUB_OUTPUT
          echo "use-filter=true" >> $GITHUB_OUTPUT
          echo "affected-modules=${AFFECTED}" >> $GITHUB_OUTPUT
          echo "::notice::Testing affected modules: ${AFFECTED}"
        fi
      fi
    else
      echo "skip-tests=false" >> $GITHUB_OUTPUT
      echo "use-filter=false" >> $GITHUB_OUTPUT
    fi

- name: cd integration-tests-jvm && mvn clean test
  if: steps.affected-modules.outputs.skip-tests != 'true'
  run: |
    cd integration-tests-jvm
    
    if [ "${{ steps.affected-modules.outputs.use-filter }}" = "true" ]; then
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -pl "${{ steps.affected-modules.outputs.affected-modules }}" -am \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip \
        --fail-at-end \
        clean test
    else
      ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
        -Dformatter.skip -Dimpsort.skip -Denforcer.skip \
        --fail-at-end \
        clean test
    fi
```

### 3.5 Integration Tests Alternative JDK Job Modification

```yaml
- name: Download Scalpel Reports
  if: github.event_name == 'pull_request' && needs.initial-mvn-install.outputs.full-build != 'true'
  uses: actions/download-artifact@v8
  with:
    name: scalpel-reports
    path: target/
  continue-on-error: true

- name: Filter Affected Modules
  id: filter-modules
  env:
    TEST_MODULES: ${{matrix.modules}}
  run: |
    if [ -f target/scalpel-report.json ]; then
      FULL_BUILD=$(jq -r '.fullBuildTriggered' target/scalpel-report.json)
      
      if [ "$FULL_BUILD" = "true" ]; then
        echo "filtered-modules=${TEST_MODULES}" >> $GITHUB_OUTPUT
        echo "skip-tests=false" >> $GITHUB_OUTPUT
      else
        # Get affected modules from Scalpel
        AFFECTED=$(jq -r '.affectedModules[] | select(.path | startswith("integration-tests/")) | .path | sub("^integration-tests/"; "")' target/scalpel-report.json | sort -u)
        
        # Filter matrix modules to only affected ones
        FILTERED=""
        for MODULE in ${TEST_MODULES//,/ }; do
          if echo "$AFFECTED" | grep -q "^${MODULE}$"; then
            if [ -z "$FILTERED" ]; then
              FILTERED="${MODULE}"
            else
              FILTERED="${FILTERED},${MODULE}"
            fi
          fi
        done
        
        if [ -z "$FILTERED" ]; then
          echo "skip-tests=true" >> $GITHUB_OUTPUT
          echo "::notice::No affected modules in this group, skipping tests"
        else
          echo "filtered-modules=${FILTERED}" >> $GITHUB_OUTPUT
          echo "skip-tests=false" >> $GITHUB_OUTPUT
          echo "::notice::Testing affected modules: ${FILTERED}"
        fi
      fi
    else
      echo "filtered-modules=${TEST_MODULES}" >> $GITHUB_OUTPUT
      echo "skip-tests=false" >> $GITHUB_OUTPUT
    fi

- name: cd integration-tests && mvn clean verify
  if: steps.filter-modules.outputs.skip-tests != 'true'
  shell: bash
  run: |
    cd integration-tests
    ../mvnw ${CQ_MAVEN_ARGS} ${BRANCH_OPTIONS} \
      -pl "${{ steps.filter-modules.outputs.filtered-modules }}" -am \
      -Dformatter.skip -Dimpsort.skip -Denforcer.skip \
      --fail-at-end \
      clean verify
```

## 4. Native Test Group Balancing

### 4.1 Current Approach (Static Balancing)

The current test-categories.yaml already provides balanced groups. With incremental builds:

1. **Per-category filtering**: Each category (group-01 through group-13) is filtered independently
2. **Skip empty categories**: Categories with no affected modules are skipped entirely
3. **Maintain balance**: Affected modules stay in their assigned groups

**Advantages:**
- Simple implementation
- Preserves existing balance
- No redistribution needed

**Limitations:**
- Some groups may have more work than others in incremental builds
- Not optimal for very small change sets

### 4.2 Future Enhancement (Dynamic Balancing)

For future optimization, the Maven mojo could be enhanced to:

1. Collect historical execution times from previous CI runs
2. Redistribute affected modules across groups based on estimated time
3. Generate new balanced groups dynamically

**Implementation approach:**
```java
@Mojo(name = "balance-test-groups")
public class BalanceTestGroupsMojo extends AbstractMojo {
    
    @Parameter(property = "cq.scalpelReportFile")
    private File scalpelReportFile;
    
    @Parameter(property = "cq.executionTimesFile")
    private File executionTimesFile; // Historical data
    
    @Parameter(property = "cq.groupCount", defaultValue = "13")
    private int groupCount;
    
    @Parameter(property = "cq.outputDir")
    private File outputDir;
    
    @Override
    public void execute() throws MojoExecutionException {
        // 1. Load affected modules
        // 2. Load historical execution times
        // 3. Use bin-packing algorithm to balance groups
        // 4. Generate new group assignments
        // 5. Write to output files (one per group)
    }
}
```

## 5. Testing Strategy

### 5.1 Local Testing

**Prerequisites:**
- Git repository with multiple commits
- Changes in specific modules

**Test Scenarios:**

1. **Single Module Change:**
   ```bash
   # Make change in extensions/kafka
   echo "// test" >> extensions/kafka/runtime/src/main/java/org/apache/camel/quarkus/component/kafka/KafkaComponent.java
   git add .
   git commit -m "test: kafka change"
   
   # Generate report
   ./mvnw validate -Dscalpel.mode=report -Dscalpel.baseBranch=HEAD~1
   
   # Verify report
   cat target/scalpel-report.json
   cat target/scalpel-impacted.log
   
   # Test Maven mojo
   ./mvnw cq:filter-test-categories \
     -Dcq.category=group-01 \
     -Dcq.scalpelReportFile=target/scalpel-report.json \
     -N
   
   cat target/filtered-modules.txt
   ```

2. **Full Build Trigger:**
   ```bash
   # Change root POM
   echo "<!-- test -->" >> pom.xml
   git add .
   git commit -m "test: pom change"
   
   # Generate report
   ./mvnw validate -Dscalpel.mode=report -Dscalpel.baseBranch=HEAD~1
   
   # Verify fullBuildTriggered=true
   jq '.fullBuildTriggered' target/scalpel-report.json
   
   # Test Maven mojo (should return all modules)
   ./mvnw cq:filter-test-categories \
     -Dcq.category=group-01 \
     -Dcq.scalpelReportFile=target/scalpel-report.json \
     -N