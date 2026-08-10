import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled JSON-schema check (Gap 4: "structured, schema-validated
 * handoffs" -- AWS Module 4) using nothing but Jackson {@link JsonNode}
 * field/type inspection -- deliberately NO external schema-validation
 * library (json-schema-validator, everit, etc.), same "hand-roll to
 * understand mechanics" reasoning phase6-mcp used for raw MCP stdio instead
 * of an SDK.
 *
 * This is intentionally narrow: required-field presence + primitive-type
 * checking + one enum check + one numeric-range check. A real system would
 * reach for a proper JSON Schema (draft 2020-12) validator the moment nested
 * objects/oneOf/conditional rules show up -- the point here is seeing what
 * "schema validation" actually mechanically does under the hood, not
 * re-implementing the spec.
 */
class HandoffSchema {

    enum FieldType { STRING, NUMBER, BOOLEAN, ARRAY, OBJECT }

    record FieldSpec(String name, FieldType type, boolean required) {
    }

    /** The exact shape a {@link TaskResult} must serialize to. */
    static final List<FieldSpec> TASK_RESULT_FIELDS = List.of(
            new FieldSpec("taskId", FieldType.STRING, true),
            new FieldSpec("state", FieldType.STRING, true),
            new FieldSpec("artifacts", FieldType.ARRAY, true),
            new FieldSpec("issues", FieldType.ARRAY, true),
            new FieldSpec("message", FieldType.STRING, true),
            new FieldSpec("confidence", FieldType.NUMBER, true)
    );

    /** The shape a {@link Task} must serialize to (kept for symmetry --
     * this demo's handoffs run TaskResult through validation, since that's
     * the "agent output -> next agent" boundary Gap 4 calls out). */
    static final List<FieldSpec> TASK_FIELDS = List.of(
            new FieldSpec("id", FieldType.STRING, true),
            new FieldSpec("type", FieldType.STRING, true),
            new FieldSpec("input", FieldType.OBJECT, true)
    );

    private HandoffSchema() {
    }

    /** Returns an empty list if {@code node} conforms to {@code schema}, else
     * one human-readable violation string per problem found (never throws --
     * the caller decides what "reject" means). */
    static List<String> validate(JsonNode node, List<FieldSpec> schema) {
        List<String> violations = new ArrayList<>();
        if (node == null || !node.isObject()) {
            violations.add("payload is not a JSON object");
            return violations;
        }

        for (FieldSpec field : schema) {
            JsonNode value = node.get(field.name());
            if (value == null || value.isNull()) {
                if (field.required()) {
                    violations.add("missing required field: '" + field.name() + "'");
                }
                continue;
            }
            if (!matchesType(value, field.type())) {
                violations.add("field '" + field.name() + "' expected type " + field.type()
                        + " but was " + value.getNodeType());
            }
        }

        // extra semantic checks beyond plain type-checking, still hand-rolled JsonNode inspection
        JsonNode state = node.get("state");
        if (state != null && state.isTextual() && !isValidTaskState(state.asText())) {
            violations.add("field 'state' has invalid enum value: '" + state.asText()
                    + "' (expected one of SUBMITTED, WORKING, DONE, FAILED)");
        }
        JsonNode confidence = node.get("confidence");
        if (confidence != null && confidence.isNumber()) {
            double c = confidence.asDouble();
            if (c < 0.0 || c > 1.0) {
                violations.add("field 'confidence' out of range [0.0, 1.0]: " + c);
            }
        }

        return violations;
    }

    private static boolean isValidTaskState(String s) {
        for (TaskState t : TaskState.values()) {
            if (t.name().equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesType(JsonNode value, FieldType type) {
        return switch (type) {
            case STRING -> value.isTextual();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
            case OBJECT -> value.isObject();
        };
    }
}
