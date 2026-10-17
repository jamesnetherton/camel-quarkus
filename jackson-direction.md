# Jackson 2 / Jackson 3 direction for the Quarkus 4 upgrade

**Context:** Branch `quarkus-4-upgrade`, using Quarkus 4.0.0.Beta1 and Camel 4.23.0. Quarkus 4 supports only Jackson 3 (`tools.jackson.*`), while Camel core and many third-party SDKs still use Jackson 2 (`com.fasterxml.jackson.*`). This note follows on from `jackson-failures-handoff.md`.

## Why dataformats-json passes

There are no forgotten workarounds. The only branch change under `integration-test-groups/dataformats-json` is the disabled `object-mapper` test and the deleted `NamingStrategyCustomizer`. It passes in native for these reasons:

- **The test POJOs register themselves.** Every model class there (`Pojo`, `Person`, `TestPojo`, `Order`, and the rest) has `@RegisterForReflection`, on `main` as well as this branch. Nothing depended on Quarkus's Jackson scanning.
- **Jackson 2 databind needs only reflection data in native.** It doesn't need Quarkus substitutions. `extensions/jackson/deployment/.../JacksonProcessor.java` already registers the few internal classes it needs, such as `JsonNode` and `JakartaXmlBindAnnotationModule`.
- **The core annotations are the same in both versions.** Jackson 3 still uses the `com.fasterxml.jackson.annotation` package (Quarkus 4's BOM pins `jackson-annotations` 2.21). `@JsonProperty`, `@JsonIgnore`, `@JsonValue` and `@JsonCreator` mean the same thing to both. Only the databind annotations moved to `tools.jackson.databind.annotation`, such as `@JsonDeserialize`, `@JsonSerialize`, `@JsonNaming` and `@JsonPOJOBuilder`.

### What this means for the failures

- **Group B (native).** Quarkus 4's `JacksonProcessor` only scans `tools.jackson.databind.annotation.JsonDeserialize` and `JsonSerialize` (checked in the 4.0.0.Beta1 class). Consul, olingo4 and twilio use the Jackson 2 `@JsonDeserialize(using=...)`, which nothing scans now. The third-party classes it points to never get registered, and those jars have no `@RegisterForReflection` to fall back on.
- **Group A (JVM) is probably misdiagnosed in the handoff doc.** Jackson 3 doesn't ignore `@JsonProperty`, since the annotation is shared. The keycloak errors look like Jackson 3's changed defaults instead:
  - "Cannot map null into type long" matches `FAIL_ON_NULL_FOR_PRIMITIVES`, which Jackson 3 turns on by default.
  - `OAuth2DeviceCodeLifespan` looks like the new property-naming rules applied to `getOAuth2DeviceCodeLifespan()`.

  This is not verified yet. If it's right, keycloak is a test-side mapper configuration fix. Telegram is a real version mix: a Jackson 2 `JsonNode` field read by Jackson 3.

## 1. Lightweight Jackson 2 native support

**Recommendation: add it.** As long as Jackson 2 is on the classpath it's needed, and it's easy to delete later. Keep the scope tight:

- **Where:** a support module such as `extensions-support/jackson2`, used only by extensions that pull Jackson 2 in through a third-party library.
- **What it does:** port only the reflection part of Quarkus 3's `JacksonProcessor`. Scan `CombinedIndexBuildItem` for the `com.fasterxml.jackson.databind.annotation.*` annotations: `JsonDeserialize` and `JsonSerialize` (`using`, `as`, `builder`, `contentUsing`, `keyUsing`), plus `JsonPOJOBuilder`, `JsonNaming` and `JsonTypeIdResolver`. Register the annotated classes and their targets.
- **What it doesn't do:** no `ObjectMapper` bean, no customizers and no config. That keeps it clear of the "don't add a Jackson 2 ObjectMapper bean" rule.
- **Catch:** it only sees indexed jars. Libraries like kiwiproject consul-client, twilio and olingo would need an `IndexDependencyBuildItem` in their extension, unless they're already indexed.
- **Doesn't cover:** openstack's `@JsonValue` and the langchain4j builder case may still need targeted registration in their own processors.

## 2. A separate camel-quarkus-jackson3 extension, or switching?

**Recommendation: switch `camel-quarkus-jackson` to `camel-jackson3`, and don't add a parallel extension.**

- **The deciding fact:** `camel-jackson3` registers the same data format name as `camel-jackson`. Both ship `META-INF/services/org/apache/camel/dataformat/jackson`, and the same applies to `jacksonXml` in `camel-jackson3xml` and `camel-jacksonxml`. Two extensions would collide on the classpath, and whichever loads first would win.
- **What switching gains:** it brings back the Quarkus `JsonMapper` bean discovery, so the disabled `object-mapper` test can return.
- **Trap to check first:** `aws2-ddb`, `google-sheets`, `mongodb` and `mongodb-gridfs` depend on `camel-quarkus-jackson` because their Camel components pull in `camel-jackson`, which is Jackson 2. After the switch, those extensions need to depend on `camel-jackson` directly, and you need to check what happens when both `jackson` service files are present. That decides whether this is a quick swap or needs Camel-side changes first.

## 3. Strategy to get the baseline merged

**Recommendation: fix where the fix is durable or test-only; disable where the fix is upstream.** Avoid workarounds in user-facing runtime code that would have to be undone later.

| Area | Approach |
|---|---|
| Group B native failures (consul, olingo4, twilio, servicenow, openstack, langchain4j-agent) | Fix with the Jackson 2 support module plus targeted registrations. This code is kept anyway. |
| keycloak, telegram (JVM) | Fix in the test: configure the mapper, or use one Jackson version on both ends. Nothing user-facing changes. |
| mcp-server, openai, jq | Disable and open tracking issues, since they wait on Quarkiverse releases for Quarkus 4. |
| Switching `camel-quarkus-jackson` to `camel-jackson3` | Separate follow-up PR. It's a user-facing change and deserves its own review and migration-guide entry. |

Each disabled test should link to an issue so the list of known-broken modules is visible.

Before opening the upstream PR, drop the fork-only commits `603349f640a` and `02771a79b74`.
