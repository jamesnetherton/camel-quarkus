# Jackson failures in the Quarkus 4 upgrade

**Context:** Branch `quarkus-4-upgrade` of camel-quarkus, using Quarkus 4.0.0.Beta1 and Camel 4.23.0. Quarkus 4 moved to Jackson 3 (`tools.jackson.*`), but Camel core and most third-party SDKs still use Jackson 2 (`com.fasterxml.jackson.*`). The proper fix is to align Camel core with Jackson 3. These failures are being held for now; any workaround needs sign-off from the maintainer.

Source: CI run https://github.com/jamesnetherton/camel-quarkus/actions/runs/38872779985

All of them come from the Jackson 2 / Jackson 3 split, in one of two ways.

## A. JVM mode: Jackson 3 reads or writes types built for Jackson 2

Jackson 3 ignores Jackson 2 annotations such as `@JsonProperty`, and it can't build Jackson 2 types such as `com.fasterxml...JsonNode`.

| Module | Failing test | Error |
|---|---|---|
| keycloak | `KeycloakAuthorizationTest`, `KeycloakClientTest`, `KeycloakErrorHandlingTest`, `KeycloakEvaluatePermissionTest` (many tests) | (1) `tools.jackson...MismatchedInputException: Cannot map null into type long`, at `RealmRepresentation["eventsExpiration"]`. (2) On the server side, `RESTEASY-JACKSON000100 ... com.fasterxml...UnrecognizedPropertyException: Unrecognized field "OAuth2DeviceCodeLifespan"` (`RealmRepresentation`). The Jackson 3 side writes property names that ignore the Jackson 2 `@JsonProperty` mappings, and the Jackson 2 side then rejects them. The test module uses `quarkus-resteasy-jackson`. |
| telegram | `TelegramTest.location` (line 164) | `tools.jackson...InvalidDefinitionException: Cannot construct instance of com.fasterxml.jackson.databind.JsonNode`, at `org.apache.camel.component.telegram.model.MessageResult["result"]`. The Camel model has a Jackson 2 `JsonNode` field, and Jackson 3 is deserializing it. |

## B. Native mode: Jackson 2 annotated classes are no longer registered for reflection

It appears Quarkus 4's Jackson extension only handles Jackson 3 annotations. So classes referenced by Jackson 2 `@JsonDeserialize(using=...)`, `@JsonValue` or `@JsonProperty`, and Jackson 2 POJOs, no longer get their reflection registration. All of these are `*IT` failures.

| Module | Failing test | Error |
|---|---|---|
| consul | `ConsulIT.basic` | `InvalidDefinitionException: Class org.kiwiproject.consul.util.UnsignedLongDeserializer has no default (no arg) constructor` |
| olingo4 | `Olingo4IT.testOlingo4CrudOperations` | `... ClientCsdlEdmx$EdmxDeserializer has no default (no arg) constructor` |
| twilio | `TwilioIT.phoneCall`, `TwilioIT.sendMessage` | `... com.twilio.converter.RFC2822Deserializer has no default (no arg) constructor` |
| servicenow | `ServicenowIT.test` | `MissingReflectionRegistrationError: ... org.apache.camel.quarkus.component.servicenow.it.generated.Incident.getActive()` |
| openstack | `OpenstackCinderSnapshotIT`, `OpenstackCinderVolume*` | `MissingReflectionRegistrationError: ... org.openstack4j.api.types.Facing.value()` (a `@JsonValue` method) |
| langchain4j-agent | `Langchain4jAgentIT.autoProvisionWithMemory`, `.agentMemory` | `InvalidDefinitionException: Builder class dev.langchain4j.data.message.UserMessage$Builder does not have build method (name: 'build')`, via `JacksonChatMessageJsonCodec.messagesFromJson` from the test's `PersistentChatMemoryStore` |

## Related failures with a different cause (not to be fixed here)

- **mcp-server and openai:** `NoClassDefFoundError: io/quarkus/jackson/ObjectMapperCustomizer` in `McpServerProcessor#addBeans`. quarkus-mcp-server 2.0.3 isn't compatible with Quarkus 4, so this needs a new upstream release.

## Possible approaches (each needs sign-off first)

1. **The proper fix:** Camel core and its components move to Jackson 3, using camel-jackson3 and Jackson 3 based models.
2. **Interim, native (group B):** a support build step that scans the index for Jackson 2 annotations (`com.fasterxml.jackson.annotation.*` and `databind.annotation.*`). It would register the annotated classes and the `using=`/`builder=` targets for reflection, as Quarkus 3's Jackson processor used to.
3. **Interim, JVM (group A):** make the keycloak and telegram test modules use Jackson 2 throughout, e.g. a Jackson 2 REST provider and a Jackson 2 RestAssured mapper, so one serializer handles both ends.

**Project policy, from earlier decisions:** tests should switch from Jackson 2 to Jackson 3 wherever the API under test allows it. User-facing Jackson extensions wait until Camel switches to camel-jackson3.

## Logs

The full CI logs (ANSI-stripped) are in `/private/tmp/claude-501/-Users-james-Projects-camel-quarkus/189753eb-368d-4517-9c3a-f52225189980/scratchpad/clean/`. Grep them for the class names above.
