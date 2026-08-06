import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Fase 3 — Anatomie van een tool call.
 *
 * Roept een echt lokaal model aan (Ollama /api/chat, model met "tools"-capability)
 * en laat de volledige tool-use loop zien:
 *
 *   1. Wij geven het model een JSON-schema van beschikbare tools (naam, beschrijving, parameters)
 *   2. Het model beslist ZELF of het een tool nodig heeft — en roept 'm niet aan, het
 *      antwoordt alleen met een STRUCTURED verzoek (naam + argumenten als JSON)
 *   3. Onze Java-code voert de tool pas uit NA validatie van die argumenten
 *      (nooit een tool blind uitvoeren met wat het model verzint)
 *   4. Het tool-resultaat gaat terug de conversatie in als een "tool"-bericht
 *   5. Het model krijgt een tweede beurt en vat het resultaat samen in natuurlijke taal
 *
 * Er is geen framework tussen ons en het model — de hele loop is met de hand
 * geschreven zodat elke stap zichtbaar is.
 *
 * Vereist Ollama lokaal met een tool-capable model:
 *   ollama pull llama3.2:3b && ollama serve
 * Draai met:  mvn -q compile exec:java -Dexec.mainClass=ToolCallingDemo
 */
public class ToolCallingDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    static final String MODEL = "llama3.2:3b";
    static final Path TRACE_FILE = Path.of("trace.jsonl");

    // ---- observability: elke tool-call gestructureerd loggen (JSONL) -------
    // in productie is dit je tracing-systeem (bv. OpenTelemetry); hier is het
    // bewust minimaal, maar het principe staat: elke actie van de agent moet
    // achteraf reconstrueerbaar zijn -- niet alleen leesbaar in de terminal.
    static void trace(String tool, JsonNode args, String status, long latencyMs, String detail) {
        ObjectNode line = JSON.createObjectNode();
        line.put("ts", Instant.now().toString());
        line.put("tool", tool);
        line.set("args", args);
        line.put("status", status); // "ok" | "error" | "stray"
        line.put("latencyMs", latencyMs);
        line.put("detail", truncate(detail, 300));
        try {
            Files.writeString(TRACE_FILE, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.out.println("(kon trace niet wegschrijven: " + e.getMessage() + ")");
        }
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ---- de "database" -----------------------------------------------------
    record Order(int id, String customer, double amount, String status) {}

    static final List<Order> ORDERS = List.of(
            new Order(1, "Jansen", 149.50, "open"),
            new Order(2, "Bakker", 42.00, "shipped"),
            new Order(3, "de Vries", 89.00, "open"),
            new Order(4, "Peters", 210.75, "cancelled"),
            new Order(5, "Visser", 15.20, "shipped")
    );

    static final Set<String> ALLOWED_STATUSES = Set.of("open", "shipped", "cancelled");

    // ---- tool-schema: wat we het model VERTELLEN dat beschikbaar is --------
    static ArrayNode toolsSchema() {
        ArrayNode tools = JSON.createArrayNode();
        ObjectNode fn = tools.addObject();
        fn.put("type", "function");
        ObjectNode function = fn.putObject("function");
        function.put("name", "get_orders");
        function.put("description", "Haal orders op uit de database, gefilterd op status.");
        ObjectNode params = function.putObject("parameters");
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode status = props.putObject("status");
        status.put("type", "string");
        ArrayNode enumNode = status.putArray("enum");
        ALLOWED_STATUSES.forEach(enumNode::add);
        status.put("description", "Filter op deze status.");
        params.putArray("required").add("status");
        return tools;
    }

    // ---- tool-uitvoering: PAS na validatie van de argumenten ---------------
    // dit is de "structured output moet gevalideerd worden" les: het model kan
    // een niet-bestaande status verzinnen (hallucinatie op argumenten), en die
    // moet je vangen voordat je 'm gebruikt -- niet erop vertrouwen dat het klopt.
    static String executeTool(String name, JsonNode args) {
        if (!"get_orders".equals(name)) {
            throw new IllegalArgumentException("onbekende tool: '" + name + "'");
        }
        JsonNode statusNode = args.get("status");
        if (statusNode == null || statusNode.isNull() || statusNode.asText().isBlank()) {
            throw new IllegalArgumentException("verplicht argument 'status' ontbreekt");
        }
        String status = statusNode.asText();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "ongeldige status '" + status + "', moet één van " + ALLOWED_STATUSES + " zijn");
        }

        ArrayNode result = JSON.createArrayNode();
        for (Order o : ORDERS) {
            if (o.status().equals(status)) {
                ObjectNode node = result.addObject();
                node.put("id", o.id());
                node.put("customer", o.customer());
                node.put("amount", o.amount());
                node.put("status", o.status());
            }
        }
        return result.toString();
    }

    // ---- één call naar Ollama /api/chat -------------------------------------
    static JsonNode chat(ArrayNode messages, ArrayNode tools) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", MODEL);
        body.put("stream", false);
        body.set("messages", messages);
        if (tools != null) body.set("tools", tools);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/chat"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root = JSON.readTree(resp.body());
        System.out.printf("         (tokens: in=%d uit=%d)%n",
                root.path("prompt_eval_count").asInt(-1), root.path("eval_count").asInt(-1));
        return root.path("message");
    }

    public static void main(String[] args) throws Exception {
        ArrayNode tools = toolsSchema();
        System.out.println("=== Tool-schema dat we het model geven ===");
        System.out.println(tools.toPrettyString());
        System.out.println();

        // -- Contrast: een vraag die GEEN tool nodig heeft ----------------------
        System.out.println("=== Stap 0: vraag zonder tool-noodzaak (contrast) ===");
        ArrayNode noToolMsgs = JSON.createArrayNode();
        noToolMsgs.addObject().put("role", "user").put("content", "Wat is in één zin een token in een LLM?");
        JsonNode direct = chat(noToolMsgs, tools);
        JsonNode strayCalls = direct.path("tool_calls");
        if (strayCalls.isArray() && !strayCalls.isEmpty()) {
            JsonNode strayFn = strayCalls.get(0).path("function");
            System.out.println("model riep TOCH een tool aan, hoewel de vraag er niets mee te maken heeft:");
            System.out.println("  " + strayFn);
            System.out.println("(bekende beperking van kleine modellen: zodra tools beschikbaar zijn, grijpen ze");
            System.out.println(" soms te snel -- dit is precies waarom validatie in stap 3 niet optioneel is)\n");
            trace(strayFn.path("name").asText(), strayFn.path("arguments"), "stray", 0,
                    "tool aangeroepen bij vraag die er niets mee te maken had -- NIET uitgevoerd");
        } else {
            System.out.println("model antwoordt direct, geen tool_calls: " + direct.path("content").asText().strip());
            System.out.println("(het model beslist zelf of een tool nodig is -- wij forceren niets)\n");
        }

        // -- De echte tool-use loop ----------------------------------------------
        ArrayNode messages = JSON.createArrayNode();
        String question = "Hoeveel orders staan er nog open, en wat is het totale bedrag? Gebruik de tool.";
        messages.addObject().put("role", "user").put("content", question);

        System.out.println("=== Stap 1: user-vraag ===");
        System.out.println(question + "\n");

        System.out.println("=== Stap 2: model vraagt om een tool aan te roepen ===");
        JsonNode assistantMsg = chat(messages, tools);
        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            System.out.println("model riep geen tool aan -- antwoord: " + assistantMsg.path("content").asText());
            return;
        }
        JsonNode call = toolCalls.get(0);
        String toolName = call.path("function").path("name").asText();
        JsonNode toolArgs = call.path("function").path("arguments");
        System.out.println("naam      : " + toolName);
        System.out.println("argumenten: " + toolArgs + "  <- dit is STRUCTURED OUTPUT, geen vrije tekst\n");
        messages.add(assistantMsg); // geschiedenis: neem het bericht 1-op-1 over

        System.out.println("=== Stap 3: validatie voordat we uitvoeren ===");
        System.out.println("-- valide aanroep --");
        long t0 = System.nanoTime();
        String toolResult = executeTool(toolName, toolArgs);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("resultaat: " + toolResult);
        trace(toolName, toolArgs, "ok", ms, toolResult);

        System.out.println("-- gesimuleerde hallucinatie: model verzint status 'pending' (bestaat niet) --");
        ObjectNode badArgs = JSON.createObjectNode().put("status", "pending");
        long t1 = System.nanoTime();
        try {
            executeTool("get_orders", badArgs);
        } catch (IllegalArgumentException e) {
            long ms2 = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("geweigerd: " + e.getMessage() + "  <- precies waarom je nooit blind mag uitvoeren\n");
            trace("get_orders", badArgs, "error", ms2, e.getMessage());
        }

        System.out.println("=== Stap 4: tool-resultaat terug de conversatie in ===");
        messages.addObject().put("role", "tool").put("content", toolResult);
        System.out.println("(role=\"tool\", content=ruwe JSON — het model leest dit als context, niet als code)\n");

        System.out.println("=== Stap 5: model vat het resultaat samen in natuurlijke taal ===");
        JsonNode finalMsg = chat(messages, tools);
        System.out.println("eindantwoord: " + finalMsg.path("content").asText().strip());

        System.out.println("\n(elke tool-call hierboven staat ook gestructureerd in " + TRACE_FILE.toAbsolutePath() + ")");
    }
}
