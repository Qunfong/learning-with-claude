import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal re-implementation of
 * {@code phase11-resilient-pipeline/HandoffSchema} + {@code HandoffValidator},
 * folded into one class - copied in, not imported.
 *
 * <p>Hand-rolled field/type inspection over a Jackson {@code JsonNode}, no
 * schema library, same "hand-roll it to understand the mechanics" ethos as the
 * rest of the repo. It answers exactly one question - <em>is this even a
 * well-formed TaskResult?</em> - and deliberately does not answer <em>is it
 * good enough to act on?</em>, which is the confidence gate's job in
 * {@link CapstoneDemo#passesConfidenceGate}. Keeping those two separate is the
 * point: a low-confidence result is still perfectly well-formed.
 */
public final class HandoffValidator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HandoffValidator() {
    }

    public static TaskResult parseAndValidate(String rawJson) {
        JsonNode root;
        try {
            root = JSON.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new SchemaValidationException(List.of("payload is not valid JSON: " + e.getOriginalMessage()));
        }

        List<String> violations = new ArrayList<>();
        requireTextual(root, "taskId", violations);
        requireTextual(root, "state", violations);
        requireTextual(root, "message", violations);
        requireArrayOfText(root, "artifacts", violations);
        requireArrayOfText(root, "issues", violations);

        if (!root.path("state").isMissingNode() && root.path("state").isTextual()
                && !TaskResult.VALID_STATES.contains(root.path("state").asText())) {
            violations.add("field 'state' must be one of " + TaskResult.VALID_STATES
                    + ", got '" + root.path("state").asText() + "'");
        }

        JsonNode confidence = root.path("confidence");
        if (confidence.isMissingNode()) {
            violations.add("field 'confidence' is missing");
        } else if (!confidence.isNumber()) {
            violations.add("field 'confidence' must be a number, got " + confidence.getNodeType());
        } else if (confidence.asDouble() < 0.0 || confidence.asDouble() > 1.0) {
            violations.add("field 'confidence' must be within [0.0, 1.0], got " + confidence.asDouble());
        }

        if (!violations.isEmpty()) {
            throw new SchemaValidationException(violations);
        }

        return new TaskResult(
                root.path("taskId").asText(),
                root.path("state").asText(),
                textList(root.path("artifacts")),
                textList(root.path("issues")),
                root.path("message").asText(),
                root.path("confidence").asDouble());
    }

    private static void requireTextual(JsonNode root, String field, List<String> violations) {
        JsonNode node = root.path(field);
        if (node.isMissingNode()) {
            violations.add("field '" + field + "' is missing");
        } else if (!node.isTextual()) {
            violations.add("field '" + field + "' must be a string, got " + node.getNodeType());
        }
    }

    private static void requireArrayOfText(JsonNode root, String field, List<String> violations) {
        JsonNode node = root.path(field);
        if (node.isMissingNode()) {
            violations.add("field '" + field + "' is missing");
            return;
        }
        if (!node.isArray()) {
            violations.add("field '" + field + "' must be an array, got " + node.getNodeType());
            return;
        }
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                violations.add("field '" + field + "' must contain only strings, found "
                        + element.getNodeType());
                return;
            }
        }
    }

    private static List<String> textList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
