import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Copied from Phase 4 (demos/phase4-agents/src/main/java/Tool.java). Neither
 * agent in this demo currently registers any tools with {@link AgentLoop}
 * (see CoderAgent's javadoc for why), but the class is kept so
 * {@link AgentLoop}'s signature is unchanged from Phase 4 and any exercise
 * extension (e.g. giving CoderAgent a `validate_syntax` tool) is a drop-in.
 *
 * @param destructive marks a tool as irreversible/dangerous — {@link AgentLoop}
 *                     routes such a call through a confirm-hook (see
 *                     {@link Guardrails}) before executing it.
 */
record Tool(String name, String description, ObjectNode parameters, boolean destructive, ToolExecutor executor) {

    interface ToolExecutor {
        String execute(JsonNode args);
    }

    ObjectNode schema(ObjectMapper json) {
        ObjectNode fn = json.createObjectNode();
        fn.put("type", "function");
        ObjectNode function = fn.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", parameters);
        return fn;
    }

    static ObjectNode emptyParams() {
        ObjectMapper json = new ObjectMapper();
        ObjectNode params = json.createObjectNode();
        params.put("type", "object");
        params.putObject("properties");
        return params;
    }

    static ObjectNode oneStringParam(String field, String description) {
        ObjectMapper json = new ObjectMapper();
        ObjectNode params = json.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode f = props.putObject(field);
        f.put("type", "string");
        f.put("description", description);
        params.putArray("required").add(field);
        return params;
    }
}
