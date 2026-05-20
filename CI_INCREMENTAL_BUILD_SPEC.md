# CI Incremental Build Specification

## Executive Summary

This specification outlines a strategy to improve Apache Camel Quarkus GitHub Actions CI workflow performance by implementing incremental builds using [Maveniverse Scalpel](https://github.com/maveniverse/scalpel). The goal is to reduce unnecessary builds and tests by identifying only the modules affected by changes in a given PR or commit.

**Expected Benefits:**
- Reduced CI execution time for PRs with limited scope
- Lower GitHub Actions compute costs
- Faster feedback for contributors
- More efficient use of runner resources

---

## Current State Analysis

### Build Pipeline Overview

The current CI workflow (`ci-build.yaml`) executes a full build and test suite on every PR and push to protected branches. The pipeline consists of the following jobs:

1. **pre-build-checks** - Validates dependabot PRs
2. **initial-mvn-install** - Builds the entire project and caches artifacts
3. **native-tests** - Runs native tests across 13 parallel groups
4. **functional-extension-tests** - Tests extensions-core, extensions, test-framework, tooling, catalog
5. **integration-tests-jvm** - JVM-only integration tests
6. **integration-tests-alternative-jdk** - Tests on JDK 21
7. **integration-tests-alternative-platform** - Tests on Windows
8. **examples-tests** - Tests example projects

### Current Build Process

#### Initial Maven Install (`initial-mvn-install` job)

```bash
# Step 1: Build everything from scratch
./mvnw clean install -DskipTests -Dquarkus.build.skip -Pformat

# Step 2: Create Maven repository tarball (~several GB)
tar -czf maven-repo.tgz -C ~ .m2/repository

# Step 3: Upload as artifact for downstream jobs
# All subsequent jobs download this artifact
```

**Key Characteristics:**
- **Full build every time** - No incremental compilation
- **Large artifact transfer** - 1-3 GB Maven repo tarball uploaded/downloaded
- **Build time** - 15-25 minutes for full build
- **Always processes all modules** - Even if changes affect only 1-2 extensions

#### Native Tests (`native-tests` job)

Tests are organized into **13 groups** defined in `tooling/scripts/test-categories.yaml`:

```yaml
group-01: [caffeine, qdrant, git, google-storage, hazelcast, ...]
group-02: [aws2, beanio, google-pubsub, grpc, ...]
group-03: [bean-validator, box, elasticsearch, ...]
# ... through group-13
```

**Execution Pattern:**
- All 13 groups run **in parallel** as a matrix strategy
- Each group:
  1. Downloads the full Maven repo artifact
  2. Extracts it (~1-2 minutes)
  3. Runs tests for ALL modules in that group
  4. Reports results

**Key Issues:**
- All groups execute even if only 1 module changed
- No skipping of unaffected groups
- Significant overhead in artifact download/extract

### Repository Structure

```
├── extensions/              # Native-supported extensions (~150+ modules)
│   ├── kafka/
│   │   ├── runtime/
│   │   └── deployment/
│   ├── aws2-s3/
│   └── ...
├── extensions-jvm/          # JVM-only extensions
├── extensions-core/         # Core extensions (core, yaml-dsl, etc.)
├── extensions-support/      # Shared support libraries
├── integration-tests/       # Individual integration test modules (~230+ modules)
│   ├── kafka/
│   ├── aws2/
│   └── ...
├── integration-test-groups/ # Grouped integration tests
│   ├── aws2/
│   ├── azure/
│   └── ...
├── integration-tests-jvm/   # JVM-only tests
├── poms/
│   ├── bom/                 # Runtime BOM
│   └── bom-deployment/      # Deployment BOM
├── tooling/                 # Maven plugins, scripts
└── docs/                    # Documentation
```

### Dependency Analysis Challenges

**Complex Dependency Graph:**
- Extensions depend on core modules (`extensions-core`, `extensions-support`)
- Integration tests depend on corresponding extensions
- Grouped tests (`integration-test-groups/*`) bundle multiple related tests
- BOM modules aggregate all runtime dependencies
- Changes to `pom.xml` in root affect all modules
- Changes to tooling affect build process globally

**Current Blind Spots:**
- No detection of which modules are impacted by a change
- No optimization for documentation-only changes
- No differentiation between core and leaf module changes

---

## Desired State: Incremental Builds with Maveniverse Scalpel

### What is Maveniverse Scalpel?

[Maveniverse Scalpel](https://github.com/maveniverse/scalpel) is a Maven extension that analyzes the project graph to determine which modules are affected by changes. It can:

- Detect changed files between Git commits/refs
- Build a dependency graph of the Maven reactor
- Calculate the minimal set of affected modules (upstream + downstream)
- Output the list in various formats (comma-separated, one per line, etc.)

**Core Capability:**
```bash
# Detect changed modules between current branch and main
mvn scalpel:detect -Dscalpel.from=origin/main -Dscalpel.to=HEAD

# Output: List of affected module paths
extensions/kafka/runtime
extensions/kafka/deployment
integration-tests/kafka
```

### Incremental Build Strategy

#### Phase 1: Change Detection (`initial-mvn-install` job)

**New Step: Detect Affected Modules**

```yaml
- name: Detect changed modules
  id: detect-changes
  run: |
    # Determine base ref for comparison
    if [[ "${{ github.event_name }}" == "pull_request" ]]; then
      BASE_REF="origin/${{ github.base_ref }}"
    else
      BASE_REF="HEAD^"
    fi
    
    # Fetch base ref for comparison
    git fetch origin ${{ github.base_ref }}:refs/remotes/origin/${{ github.base_ref }}
    
    # Use Maveniverse Scalpel to detect affected modules
    ./mvnw scalpel:detect \
      -Dscalpel.from=${BASE_REF} \
      -Dscalpel.to=HEAD \
      -Dscalpel.output=changed-modules.txt
    
    # Parse affected modules
    if [[ -s changed-modules.txt ]]; then
      CHANGED_MODULES=$(cat changed-modules.txt)
      echo "changed-modules=${CHANGED_MODULES}" >> $GITHUB_OUTPUT
      echo "has-changes=true" >> $GITHUB_OUTPUT
    else
      echo "has-changes=false" >> $GITHUB_OUTPUT
    fi
    
    # Detect if core/BOM/tooling changed (affects everything)
    if grep -qE '^(poms/|tooling/|extensions-core/|extensions-support/)' changed-modules.txt; then
      echo "core-changed=true" >> $GITHUB_OUTPUT
    else
      echo "core-changed=false" >> $GITHUB_OUTPUT
    fi
```

**Output Variables:**
- `changed-modules` - Comma-separated list of affected module paths
- `has-changes` - Boolean indicating if any modules changed
- `core-changed` - Boolean indicating if core infrastructure changed

#### Phase 2: Conditional Build

**Modified Build Step:**

```yaml
- name: Incremental mvn install
  if: steps.detect-changes.outputs.has-changes == 'true'
  run: |
    if [[ "${{ steps.detect-changes.outputs.core-changed }}" == "true" ]]; then
      # Full build if core changed
      ./mvnw ${CQ_MAVEN_ARGS} clean install -DskipTests -Dquarkus.build.skip -Pformat
    else
      # Incremental build of affected modules
      MODULES="${{ steps.detect-changes.outputs.changed-modules }}"
      ./mvnw ${CQ_MAVEN_ARGS} clean install \
        -pl "${MODULES}" -am \
        -DskipTests -Dquarkus.build.skip -Pformat
    fi
```

**Explanation:**
- `-pl "${MODULES}"` - Build only listed modules
- `-am` (also-make) - Also build upstream dependencies
- If core changed → full build (safety)
- If only leaf modules changed → incremental build

#### Phase 3: Dynamic Test Group Matrix

**New Step: Filter Test Categories**

```yaml
- name: Setup Filtered Native Test Matrix
  id: set-native-matrix
  run: |
    # Get list of changed integration test modules
    CHANGED_ITESTS=$(echo "${{ steps.detect-changes.outputs.changed-modules }}" | \
      grep -E '^integration-tests/' | \
      sed 's|^integration-tests/||' || true)
    
    if [[ -z "${CHANGED_ITESTS}" ]] || [[ "${{ steps.detect-changes.outputs.core-changed }}" == "true" ]]; then
      # Run all groups if core changed or no specific tests changed
      CATEGORIES=$(yq -M -N -I 0 -o=json e 'keys' tooling/scripts/test-categories.yaml | tr '"' "'")
      echo "matrix={'category': ${CATEGORIES}}" >> $GITHUB_OUTPUT
    else
      # Filter groups to only those containing changed tests
      ACTIVE_GROUPS=()
      for GROUP in $(yq e 'keys | .[]' tooling/scripts/test-categories.yaml); do
        GROUP_MODULES=$(yq e ".${GROUP}[]" tooling/scripts/test-categories.yaml)
        for CHANGED in ${CHANGED_ITESTS}; do
          if echo "${GROUP_MODULES}" | grep -q "^${CHANGED}$"; then
            ACTIVE_GROUPS+=("${GROUP}")
            break
          fi
        done
      done
      
      if [[ ${#ACTIVE_GROUPS[@]} -eq 0 ]]; then
        # No integration tests affected
        echo "matrix={'category': []}" >> $GITHUB_OUTPUT
      else
        CATEGORIES=$(printf '%s\n' "${ACTIVE_GROUPS[@]}" | jq -R . | jq -s -c .)
        echo "matrix={'category': ${CATEGORIES}}" >> $GITHUB_OUTPUT
      fi
    fi
    
    echo "Active test groups: ${ACTIVE_GROUPS[@]:-none}"
```

**Output:**
- Dynamic matrix with only affected groups
- Empty matrix if no integration tests changed
- Full matrix if core changed (safety)

#### Phase 4: Conditional Test Execution

**Modified Native Tests Job:**

```yaml
native-tests:
  name: Native Tests - ${{matrix.category}}
  needs: initial-mvn-install
  runs-on: ubuntu-latest
  if: |
    needs.initial-mvn-install.outputs.matrix != '{"category": []}' &&
    (github.event_name != 'pull_request' || !contains(github.event.pull_request.labels.*.name, 'JVM'))
  strategy:
    fail-fast: false
    matrix: ${{ fromJson(needs.initial-mvn-install.outputs.matrix) }}
  # ... rest of job
```

**Key Change:**
- Skip job entirely if matrix is empty (no affected tests)

### Incremental Build Decision Matrix

| Change Type | Build Strategy | Test Strategy |
|-------------|---------------|---------------|
| Documentation only (`.adoc`, `.md`) | Skip build | Skip all tests |
| Root `pom.xml` | Full build | Run all tests |
| `poms/bom/*` | Full build | Run all tests |
| `tooling/*` | Full build | Run all tests |
| `extensions-core/*` | Full build | Run all tests |
| `extensions-support/*` | Full build | Run all tests |
| Single extension (e.g., `extensions/kafka`) | Build extension + downstream | Run tests for that extension only |
| Multiple unrelated extensions | Build affected extensions + downstream | Run tests for affected groups only |
| Integration test only | Build that test module | Run that test's group only |
| Grouped integration test | Build that grouped module | Run all tests in that group |

### Maven Repository Artifact Optimization

**Current:**
```yaml
# Upload entire .m2/repository (~2-3 GB)
tar -czf maven-repo.tgz -C ~ .m2/repository
```

**Optimized (for incremental builds):**
```yaml
# Upload only project artifacts + essential dependencies
tar -czf maven-repo.tgz -C ~ \
  .m2/repository/org/apache/camel/quarkus \
  .m2/repository/io/quarkus \
  .m2/repository/org/apache/camel
```

**Benefits:**
- Smaller artifact (500 MB - 1 GB vs 2-3 GB)
- Faster upload/download
- Tests will download missing dependencies on-demand (cached by GitHub Actions)

### Edge Cases and Safety Mechanisms

#### Force Full Build Override

Add a label to allow contributors to force a full build:

```yaml
if: |
  steps.detect-changes.outputs.core-changed == 'true' ||
  contains(github.event.pull_request.labels.*.name, 'ci:full-build')
```

#### Dependency Graph Cache

Cache the dependency graph to speed up subsequent runs:

```yaml
- name: Cache Scalpel dependency graph
  uses: actions/cache@v4
  with:
    path: ~/.m2/.scalpel-cache
    key: scalpel-graph-${{ hashFiles('**/pom.xml') }}
```

#### Fallback on Detection Failure

```yaml
- name: Detect changed modules
  id: detect-changes
  continue-on-error: true
  run: |
    # ... scalpel detection ...

- name: Set fallback mode
  if: failure()
  run: |
    echo "core-changed=true" >> $GITHUB_OUTPUT
```

If Scalpel fails, fall back to full build for safety.

### Documentation-Only Changes

Already handled by `paths-ignore` in workflow triggers:

```yaml
paths-ignore:
  - '**.adoc'
  - '**.md'
```

Changes to documentation skip the workflow entirely.

---

## Implementation Plan

### Prerequisites

1. **Add Scalpel Dependency**
   - Add to `.mvn/extensions.xml` or project `pom.xml`
   - Version: Latest stable (check https://github.com/maveniverse/scalpel)

2. **Test Locally**
   ```bash
   # Add scalpel to build
   ./mvnw scalpel:detect -Dscalpel.from=origin/main -Dscalpel.to=HEAD
   ```

### Phase 1: Detection Implementation (Week 1)

- [ ] Add Scalpel Maven extension to project
- [ ] Create detection step in `initial-mvn-install` job
- [ ] Test detection logic with various change scenarios
- [ ] Validate output format and parsing
- [ ] Create unit tests for detection script

### Phase 2: Incremental Build (Week 2)

- [ ] Implement conditional build logic based on detection
- [ ] Add core/non-core change differentiation
- [ ] Test incremental builds with different module combinations
- [ ] Validate Maven reactor behavior with `-pl` and `-am`
- [ ] Ensure BOM and metadata files are generated correctly

### Phase 3: Dynamic Test Matrix (Week 3)

- [ ] Implement test category filtering logic
- [ ] Create matrix reduction for native tests
- [ ] Add similar logic for alternative JDK tests
- [ ] Test with empty matrices (no tests to run)
- [ ] Validate that unaffected tests are properly skipped

### Phase 4: Optimization & Safety (Week 4)

- [ ] Optimize Maven repository artifact size
- [ ] Add `ci:full-build` label support
- [ ] Implement fallback mechanisms
- [ ] Add detection metrics/logging
- [ ] Create dashboard to track incremental build savings

### Phase 5: Rollout (Week 5)

- [ ] Deploy to a test branch first
- [ ] Monitor several PRs for correctness
- [ ] Gather performance metrics
- [ ] Merge to main branch
- [ ] Document behavior for contributors

---

## Validation & Testing

### Test Scenarios

| Scenario | Expected Behavior |
|----------|-------------------|
| Change only `extensions/kafka/runtime/src/...` | Build kafka extension + kafka integration test only |
| Change `poms/bom/pom.xml` | Full build, all tests |
| Change `tooling/maven-plugin/...` | Full build, all tests |
| Change `extensions/kafka` + `extensions/aws2-s3` | Build both extensions + their tests, skip others |
| Change only `docs/modules/...` | Workflow skipped entirely |
| Change `integration-tests/kafka/src/test/...` | Build kafka itest only, run group-01 tests |
| No changes (merge commit) | Full build (safety) |

### Success Metrics

**Performance:**
- Average PR build time reduced by 40-60% for focused changes
- Artifact upload/download time reduced by 30-50%
- Runner minutes saved per PR: 20-40 minutes

**Reliability:**
- Zero false negatives (missed tests that should have run)
- < 1% false positives (ran more tests than necessary)
- No broken builds due to incomplete dependency resolution

### Monitoring

Add logging to track:
```yaml
- name: Report incremental build stats
  run: |
    echo "::notice::Changed modules: $(wc -l < changed-modules.txt)"
    echo "::notice::Active test groups: ${#ACTIVE_GROUPS[@]}"
    echo "::notice::Skipped groups: $((13 - ${#ACTIVE_GROUPS[@]}))"
```

---

## Risks and Mitigations

### Risk 1: Incomplete Dependency Detection

**Risk:** Scalpel might miss transitive dependencies or runtime-only dependencies.

**Mitigation:**
- Always include `-am` (also-make) flag to build upstream
- Force full build when core modules change
- Maintain `ci:full-build` label for manual override
- Monitor test failures and adjust patterns

### Risk 2: False Sense of Security

**Risk:** Skipping tests might hide integration issues between unaffected modules.

**Mitigation:**
- Still run full builds on main branch after merge
- Periodic full build cron jobs (nightly)
- Require full build before releases
- Keep `paths-ignore` conservative

### Risk 3: Complexity Increase

**Risk:** Incremental build logic adds complexity to CI pipeline.

**Mitigation:**
- Extensive documentation
- Clear fallback to full build on any errors
- Monitoring and alerting for detection failures
- Gradual rollout with validation

### Risk 4: Grouped Tests Coupling

**Risk:** `integration-test-groups/*` modules bundle multiple tests, changes to one require running all in group.

**Mitigation:**
- This is expected behavior (grouped by design)
- Still saves time by skipping other groups
- Consider future work to further decompose groups if needed

---

## Future Enhancements

1. **Machine Learning for Flaky Test Detection**
   - Analyze historical test failures
   - Auto-retry flaky tests
   - Reduce noise in CI results

2. **Parallel Build Optimization**
   - Use `-T` flag with incremental builds
   - Further reduce build time with parallel reactor

3. **Smarter Artifact Caching**
   - Use GitHub Actions cache instead of artifacts
   - Cache per extension
   - Deduplicate unchanged artifacts

4. **Build Time Prediction**
   - Show estimated time savings in PR comments
   - Track trends over time
   - Identify slowest modules for optimization

5. **Integration with GitHub Apps**
   - Bot comment showing what will be tested
   - Interactive selection of additional test groups
   - Build status preview before running

---

## References

- **Maveniverse Scalpel:** https://github.com/maveniverse/scalpel
- **Maven Reactor:** https://maven.apache.org/guides/mini/guide-multiple-modules.html
- **GitHub Actions Matrix Strategy:** https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs
- **Camel Quarkus Test Categories:** `tooling/scripts/test-categories.yaml`
- **Current CI Workflow:** `.github/workflows/ci-build.yaml`

---

## Conclusion

Implementing incremental builds with Maveniverse Scalpel will significantly improve the Camel Quarkus CI pipeline efficiency. By intelligently detecting and building only affected modules, we can reduce PR feedback time, lower compute costs, and improve contributor experience while maintaining test coverage and reliability through safety mechanisms and fallback strategies.

**Key Success Factors:**
- Conservative approach (full build when in doubt)
- Extensive testing before rollout
- Clear documentation for contributors
- Monitoring and continuous improvement
