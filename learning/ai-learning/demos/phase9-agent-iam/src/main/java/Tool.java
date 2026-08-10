import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Copied in and adapted from {@code phase4-agents/Tool.java} (same schema +
 * executor split, same discipline of "the executor only runs after
 * validation"). The addition here is {@code requiredScope}: the single
 * capability string a caller's {@link ScopedCredential} must hold before
 * {@link SecureToolInvoker} will ever call {@link #executor()}. The LLM
 * still only ever sees {@link #name()}/{@link #description()}/{@link #parameters()}
 * via {@link #schema} — {@code requiredScope} is enforced entirely
 * out-of-band by {@link Guardrails#authorize}, never surfaced to the model.
 *
 * @param destructive    same meaning as phase4: routes through a confirm-hook
 * @param requiredScope  capability string, e.g. {@code "read:customer"}, checked
 *                       against the caller's credential before execution
 */
record Tool(String name, String description, ObjectNode parameters, boolean destructive,
            String requiredScope, ToolExecutor executor) {

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
