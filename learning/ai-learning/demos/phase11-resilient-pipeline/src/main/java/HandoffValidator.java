import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * The actual boundary gate: parses a raw JSON string (what an agent -- LLM
 * or otherwise -- claims is its {@link TaskResult}) and refuses to hand it
 * onward unless it passes {@link HandoffSchema#validate}. This is the piece
 * that makes Gap 4's "schema-validated handoffs" real rather than
 * decorative: {@link PlannerAgent}/{@link CoderAgent}/{@link ReviewerAgent}
 * all route their model's raw text through here before
 * {@link ResilientPipeline} ever sees a {@link TaskResult} object.
 *
 * Deliberately NOT lenient: a malformed payload is a hard
 * {@link SchemaValidationException}, never a silent coercion (e.g.
 * {@code "confidence":"very confident"} does NOT get defaulted to 0.0 and
 * waved through -- see the test suite's
 * {@code rejectsMalformedTaskResult_doesNotSilentlyCoerce}).
 */
class HandoffValidator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HandoffValidator() {
    }

    /** Parses {@code rawJson} and validates it against
     * {@link HandoffSchema#TASK_RESULT_FIELDS}. Throws
     * {@link SchemaValidationException} on ANY violation (including
     * unparseable JSON, reported as its own violation) -- never returns a
     * partially-valid or coerced result. */
    static TaskResult parseAndValidate(String rawJson) {
        JsonNode node;
        try {
            node = JSON.readTree(rawJson);
        } catch (Exception e) {
            throw new SchemaValidationException(List.of("payload is not valid JSON: " + e.getMessage()));
        }

        List<String> violations = HandoffSchema.validate(node, HandoffSchema.TASK_RESULT_FIELDS);
        if (!violations.isEmpty()) {
            throw new SchemaValidationException(violations);
        }

        try {
            return JSON.treeToValue(node, TaskResult.class);
        } catch (Exception e) {
            // Should be unreachable once schema validation above passed -- if this ever
            // fires it means the schema is out of sync with the TaskResult record shape.
            throw new SchemaValidationException(List.of("schema passed but deserialization failed: " + e.getMessage()));
        }
    }
}
