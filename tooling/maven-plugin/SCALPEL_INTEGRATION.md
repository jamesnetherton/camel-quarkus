# Scalpel Integration POC

## Overview

Scalpel can replace ~200 lines of our custom JGit logic. Here's how:

## Current Architecture (with custom JGit)

```
DetectChangedModulesMojo
├── JGit operations (~200 lines)
│   ├── Find git repository
│   ├── Resolve base branch/commit
│   ├── Calculate merge base
│   ├── Get file diffs
│   └── Map files to modules
├── Categorize modules
├── Map to test categories
└── Generate matrices
```

## Proposed Architecture (with Scalpel)

```
Scalpel Extension (external)
├── JGit operations (handled by Scalpel)
├── File-to-module mapping (handled by Scalpel)
└── Transitive dependencies (handled by Scalpel)
    ↓
    outputs: target/scalpel-report.json

DetectChangedModulesMojo (simplified)
├── Read scalpel-report.json (~10 lines)
├── Categorize modules
├── Map to test categories
└── Generate matrices
```

## Scalpel Output Format

```json
{
  "version": "1",
  "scalpelVersion": "0.1.0",
  "baseBranch": "origin/main",
  "fullBuildTriggered": false,
  "affectedModules": [
    {
      "groupId": "org.apache.camel.quarkus",
      "artifactId": "camel-quarkus-kafka-runtime",
      "path": "extensions/kafka/runtime",
      "reasons": ["SOURCE_CHANGE"]
    },
    {
      "groupId": "org.apache.camel.quarkus",
      "artifactId": "camel-quarkus-integration-test-kafka",
      "path": "integration-tests/kafka",
      "reasons": ["TRANSITIVE_DEPENDENCY"]
    }
  ]
}
```

## Integration Steps

### 1. Add Scalpel Extension

```bash
# Copy example and enable
cp .mvn/extensions-scalpel.xml.example .mvn/extensions.xml
```

### 2. Configure Scalpel

Add to `.mvn/maven.config`:
```
-Dscalpel.mode=report
-Dscalpel.alsoMakeDependents=true
```

### 3. Simplify Maven Plugin

**Before (current):**
```java
@Mojo(name = "detect-changed-modules")
public class DetectChangedModulesMojo {
    // ~200 lines of JGit code
    private Set<String> detectChangedFilesWithJGit() { ... }
    private ObjectId resolveCommit() { ... }
    private ObjectId findMergeBase() { ... }
    private Set<String> mapFilesToModules() { ... }
    private Set<String> analyzeTransitiveDependents() { ... }
    
    // ~100 lines of categorization/matrix generation
    private Map<String, Set<String>> categorizeModules() { ... }
    private Map<String, Object> generateNativeTestsMatrix() { ... }
}
```

**After (with Scalpel):**
```java
@Mojo(name = "detect-changed-modules")
public class DetectChangedModulesMojo {
    // ~10 lines to read Scalpel output
    private Set<String> readScalpelReport() {
        Path reportPath = project.getBasedir().toPath().resolve("target/scalpel-report.json");
        ObjectMapper mapper = new ObjectMapper();
        ScalpelReport report = mapper.readValue(reportPath.toFile(), ScalpelReport.class);
        
        if (report.isFullBuildTriggered()) {
            return null; // Full build
        }
        
        return report.getAffectedModules().stream()
            .map(ScalpelModule::getPath)
            .collect(Collectors.toSet());
    }
    
    // ~100 lines of categorization/matrix generation (unchanged)
    private Map<String, Set<String>> categorizeModules() { ... }
    private Map<String, Object> generateNativeTestsMatrix() { ... }
}
```

### 4. Update Workflow

```yaml
- name: Detect Changed Modules
  run: |
    # Generate Scalpel report
    ./mvnw validate \
      -Dscalpel.mode=report \
      -Dscalpel.baseBranch=${{ steps.base-commit.outputs.base-commit }} \
      -Dscalpel.head=HEAD
    
    # Transform to workflow matrices
    ./mvnw -N cq:detect-changed-modules \
      -Dcq.scalpelReport=target/scalpel-report.json \
      -Dcq.outputFile=${{ runner.temp }}/changed-modules.json
```

## Benefits

### Code Reduction
- **Remove:** ~200 lines of JGit operations
- **Keep:** ~100 lines of test categorization/matrix generation
- **Result:** 67% less code in our plugin

### Better Change Detection
Scalpel is smarter about POM changes:

**Current approach:**
```
pom.xml changed → All modules rebuild
```

**Scalpel approach:**
```
pom.xml formatting change → Ignored
<kafka.version> change → Only modules using ${kafka.version}
New <dependency> → Only modules inheriting it
```

### Community Maintained
- No need to maintain git detection logic
- Bug fixes from Maven community
- Well-tested across many projects

## Migration Effort

### Low Risk Changes
1. Add `.mvn/extensions.xml` (1 file)
2. Update workflow to run `mvn validate` first (2 lines)
3. Update plugin to read JSON instead of JGit (~20 lines changed)

### Testing Strategy
1. Run both implementations in parallel
2. Compare outputs
3. Verify incremental builds work correctly
4. Switch over once validated

## Scalpel Features We Get For Free

1. **Smart POM analysis** - Ignores cosmetic changes, tracks property inheritance
2. **Multiple CI support** - Auto-detects GitHub Actions, GitLab CI, Jenkins
3. **Full build triggers** - Configurable patterns for files that always trigger full build
4. **Transitive analysis** - Built-in dependency graph traversal
5. **Performance** - Optimized for large multi-module projects

## Potential Issues

1. **Extension lifecycle** - Scalpel runs during Maven initialization, need to ensure report is generated before our plugin runs
2. **Base commit format** - Need to verify Scalpel accepts commit SHAs (not just branch names)
3. **Missing report handling** - What if Scalpel doesn't run or fails?

## Recommendation

**YES - Worth migrating** because:
- ✅ Significant code reduction (67% less code)
- ✅ Better change detection (smart POM analysis)
- ✅ Community maintained (less burden)
- ✅ Low migration risk (can validate side-by-side)
- ✅ Scalpel is actively maintained (released April 2026)

**Next Steps:**
1. Test Scalpel on this repo (see if it works with our structure)
2. Compare Scalpel output vs. our current detection
3. Create working POC with simplified plugin
4. Validate in CI before full migration
