# Testing Incremental Builds Locally

This guide shows you how to test the incremental build detection plugin locally before using it in CI.

## Prerequisites

1. You're in the root directory of the camel-quarkus project
2. You have a git repository with commits
3. Maven is installed

## Step 1: Build the Maven Plugin

First, build and install the maven plugin locally:

```bash
cd tooling/maven-plugin
../../mvnw clean install
cd ../..
```

## Step 2: Basic Test - Compare Against Main Branch

Test the plugin by comparing your current branch against `main`:

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json
```

Or use the short form (after the plugin is installed):

```bash
./mvnw camel-quarkus:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json
```

**Expected Output**:
```
[INFO] Comparing changes against branch: origin/main
[INFO] Detected 5 changed files
[INFO] Changed modules: [extensions/kafka, integration-tests/kafka]
[INFO] Affected modules (including dependents): [extensions/kafka, integration-tests/kafka, integration-tests/kafka-sasl]
[INFO] Written result to: /path/to/camel-quarkus/target/changed-modules.json
```

## Step 3: View the Results

Check the generated JSON file:

```bash
cat target/changed-modules.json
```

**Example Output**:
```json
{
  "full-build" : false,
  "changed-modules" : [ "extensions/kafka", "integration-tests/kafka", "integration-tests/kafka-sasl" ],
  "native-tests" : {
    "include" : [ {
      "category" : "group-01"
    } ]
  },
  "functional-extension-tests" : {
    "modules" : [ "extensions/kafka" ]
  },
  "extensions-jvm-tests" : {
    "modules" : [ ]
  },
  "integration-tests-alternative-jdk" : {
    "modules" : [ "kafka", "kafka-sasl" ]
  }
}
```

## Step 4: Test Different Scenarios

### Scenario 1: Test with a Specific Branch

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/camel-main \
  -DoutputFile=target/changed-modules.json
```

### Scenario 2: Test with Local Branch

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=main \
  -DoutputFile=target/changed-modules.json
```

### Scenario 3: Force Full Build

```bash
FORCE_FULL_BUILD=true ./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DoutputFile=target/changed-modules.json
```

**Expected Output**:
```json
{
  "full-build" : true,
  "changed-modules" : [ ]
}
```

### Scenario 4: Test Core Module Changes

Make a change to a core module and test:

```bash
# Make a dummy change
echo "# test" >> poms/pom.xml

# Run detection
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# Revert the change
git checkout poms/pom.xml
```

**Expected Output**: Should trigger full build because `poms/` is a core module.

### Scenario 5: Test Without Transitive Dependencies

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DincludeTransitiveDependents=false \
  -DoutputFile=target/changed-modules.json
```

This will only include directly changed modules, not their dependents.

### Scenario 6: Custom Output Location

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=/tmp/my-changes.json

cat /tmp/my-changes.json
```

## Step 5: Test with Pretty Printing

Use `jq` to pretty-print and analyze the output:

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# View full output
cat target/changed-modules.json | jq .

# View only changed modules
cat target/changed-modules.json | jq '.["changed-modules"]'

# View native test categories
cat target/changed-modules.json | jq '.["native-tests"]'

# Check if full build is required
cat target/changed-modules.json | jq '.["full-build"]'
```

## Step 6: Simulate CI Workflow

Test the complete workflow locally:

```bash
# 1. Build the project (like initial-mvn-install)
./mvnw clean install -DskipTests

# 2. Detect changes
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# 3. Check if full build is needed
FULL_BUILD=$(cat target/changed-modules.json | jq -r '.["full-build"]')
echo "Full build required: $FULL_BUILD"

# 4. Get affected native test categories
if [ "$FULL_BUILD" = "false" ]; then
  CATEGORIES=$(cat target/changed-modules.json | jq -r '.["native-tests"].include[].category')
  echo "Affected test categories: $CATEGORIES"
fi
```

## Step 7: Test Error Handling

### Test with Invalid Branch

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/nonexistent-branch \
  -DoutputFile=target/changed-modules.json
```

**Expected**: Should fall back to full build with warning message.

### Test Outside Git Repository

```bash
cd /tmp
mkdir test-no-git
cd test-no-git

# This should fail gracefully
mvn org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DoutputFile=changed-modules.json
```

**Expected**: Should fall back to full build with "Not a git repository" warning.

## Step 8: Debug Mode

Run with Maven debug output to see detailed logging:

```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json \
  -X
```

This will show:
- Git operations (fetch, resolve, diff)
- Module mapping logic
- Dependency graph construction
- Category mapping

## Step 9: Test on a Feature Branch

Create a test branch and make changes:

```bash
# Create a test branch
git checkout -b test-incremental-build

# Make a change to an extension
echo "// test change" >> extensions/kafka/runtime/src/main/java/org/apache/camel/quarkus/component/kafka/CamelKafkaProducer.java

# Commit the change
git add .
git commit -m "test: incremental build detection"

# Run detection
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# View results
cat target/changed-modules.json | jq .

# Clean up
git checkout main
git branch -D test-incremental-build
```

## Step 10: Verify Dependency Analysis

Test that transitive dependencies are correctly detected:

```bash
# Make a change to a core extension
echo "// test" >> extensions-core/core/runtime/src/main/java/org/apache/camel/quarkus/core/CamelQuarkusCore.java

# Run detection
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json

# This should trigger full build because extensions-core is a core module
cat target/changed-modules.json | jq '.["full-build"]'

# Revert
git checkout extensions-core/core/runtime/src/main/java/org/apache/camel/quarkus/core/CamelQuarkusCore.java
```

## Common Issues and Solutions

### Issue: "Could not resolve base branch"

**Solution**: Fetch the remote branch first:
```bash
git fetch origin main
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules -DbaseBranch=origin/main
```

### Issue: "No changes detected" when you know there are changes

**Solution**: Check your current branch and commits:
```bash
git status
git log --oneline origin/main..HEAD
```

### Issue: Plugin not found

**Solution**: Build and install the plugin first:
```bash
cd tooling/maven-plugin
../../mvnw clean install
cd ../..
```

## Quick Reference

### Minimal Command (Full Coordinates)
```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules
```
Uses defaults: `baseBranch=origin/main`, `outputFile=target/changed-modules.json`

### Short Form (After Plugin Install)
```bash
./mvnw camel-quarkus:detect-changed-modules
```

### Full Command with All Options
```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules \
  -DbaseBranch=origin/main \
  -DoutputFile=target/changed-modules.json \
  -DincludeTransitiveDependents=true \
  -DtestCategoriesFile=tooling/scripts/test-categories.yaml \
  -DforceFullBuild=false
```

### One-Liner to Test and View
```bash
./mvnw org.apache.camel.quarkus:camel-quarkus-maven-plugin:3.32.0-SNAPSHOT:detect-changed-modules -DbaseBranch=origin/main && cat target/changed-modules.json | jq .
```

## Next Steps

After testing locally:
1. Commit your changes to a feature branch
2. Push to GitHub
3. Create a PR to see the incremental build in action
4. Monitor the CI workflow to verify it works as expected

## Troubleshooting

If you encounter issues:
1. Check Maven output for error messages
2. Run with `-X` flag for debug output
3. Verify git repository state with `git status`
4. Ensure the plugin is installed: `ls ~/.m2/repository/org/apache/camel/quarkus/camel-quarkus-maven-plugin/`
5. Check the generated JSON file for clues: `cat target/changed-modules.json`