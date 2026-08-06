import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Eén tool: schema (wat we het model VERTELLEN) + executor (wat er ECHT
 * gebeurt, pas na validatie in de executor zelf — zelfde discipline als
 * fase3's {@code executeTool}).
 *
 * @param destructive markeert een tool als "onomkeerbaar/gevaarlijk" — de
 *                     {@link AgentLoop} routeert zo'n aanroep door een
 *                     confirm-hook (zie {@link Guardrails}) vóór uitvoering.
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
