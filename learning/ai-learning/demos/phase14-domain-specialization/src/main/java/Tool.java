import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Copied unchanged from phase4-agents/Tool.java (see phase4's README for the
 * full rationale). One tool: schema (what we tell the model) + executor (what
 * REALLY happens, validated inside the executor itself).
 *
 * @param destructive marks a tool as "irreversible/dangerous" — the
 *                     {@link AgentLoop} routes such a call through a
 *                     confirm-hook (see {@link Guardrails}) before execution.
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

    static ObjectNode twoParams(String field1, String type1, String desc1,
                                 String field2, String type2, String desc2) {
        ObjectMapper json = new ObjectMapper();
        ObjectNode params = json.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode f1 = props.putObject(field1);
        f1.put("type", type1);
        f1.put("description", desc1);
        ObjectNode f2 = props.putObject(field2);
        f2.put("type", type2);
        f2.put("description", desc2);
        params.putArray("required").add(field1).add(field2);
        return params;
    }
}
