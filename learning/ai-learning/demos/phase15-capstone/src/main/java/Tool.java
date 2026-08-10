import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * Minimal re-implementation of {@code phase4-agents/Tool} as adapted by
 * {@code phase9-agent-iam} - copied in, not imported.
 *
 * <p>The load-bearing detail is {@link #schema()}: it emits {@code name},
 * {@code description} and {@code parameters} and <b>nothing else</b>. The
 * model never sees {@link #requiredScope()} and there is no field anywhere in
 * this shape for a credential to travel in - that separation is structural,
 * not a convention someone has to remember (see {@link SecureToolInvoker}).
 *
 * @param requiredScope the capability a caller's credential must hold before
 *                      {@code executor} is allowed to run
 */
public record Tool(String name, String description, ObjectNode parameters, String requiredScope,
                   Function<JsonNode, String> executor) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Exactly what a model would be shown. Note the absence of requiredScope. */
    public ObjectNode schema() {
        ObjectNode node = JSON.createObjectNode();
        node.put("name", name);
        node.put("description", description);
        node.set("parameters", parameters);
        return node;
    }

    /** Convenience builder for the single-string-parameter tools used in this demo. */
    public static ObjectNode oneStringParam(String field, String description) {
        ObjectNode params = JSON.createObjectNode();
        params.put("type", "object");
        ObjectNode properties = params.putObject("properties");
        ObjectNode f = properties.putObject(field);
        f.put("type", "string");
        f.put("description", description);
        params.putArray("required").add(field);
        return params;
    }
}
