import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Fase 6 -- TWEEDE MCP-server, om te laten zien waarom multi-server MCP
 * meer oplevert dan één server: {@link McpServer} kent alleen STATISCHE
 * code (regels/methodes/TODO's). Deze server kent alleen RUNTIME-gedrag
 * (`trace.jsonl` uit fase4 -- ECHTE tool-aanroepen van eerdere agent-runs,
 * geen gesimuleerde data). Geen van beide servers kan alleen de vraag
 * beantwoorden "welk bestand is het risicovolst?" -- dat vereist BEIDE
 * databronnen, en de agent (MCP-client) is degene die ze combineert door
 * over twee servers heen tool-calls te doen binnen ÉÉN taak.
 *
 * Tool -> bestand is bewust hardcoded (geen runtime-introspectie nodig):
 * elke fase4-tool is een static method reference naar precies één klasse.
 *
 * Draai apart van {@link McpServer} via: mvn -q exec:java -Dexec.mainClass=TraceStatsServer
 */
public class TraceStatsServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path TRACE_FILE = resolveTraceFile();

    // welke fase4-tool wordt geïmplementeerd in welk bronbestand
    private static final Map<String, String> TOOL_TO_FILE = Map.of(
            "check_inbox", "AgentLoopDemo.java",
            "delete_all_messages", "GuardrailsDemo.java",
            "read_file", "CodingAgentDemo.java",
            "write_file", "CodingAgentDemo.java",
            "run_tests", "CodingAgentDemo.java",
            "remember", "MemoryDemo.java",
            "recall", "MemoryDemo.java");

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            ObjectNode response = handle(line);
            if (response != null) {
                out.println(response.toString());
                out.flush();
            }
        }
    }

    private static Path resolveTraceFile() {
        Path here = Path.of("").toAbsolutePath();
        Path demosDir = here.endsWith("phase6-mcp") ? here.getParent() : here.resolve("demos");
        return demosDir.resolve("phase4-agents").resolve("trace.jsonl");
    }

    private static ObjectNode handle(String requestLine) {
        JsonNode req;
        try {
            req = JSON.readTree(requestLine);
        } catch (Exception e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }

        JsonNode id = req.get("id");
        String method = req.path("method").asText("");
        JsonNode params = req.path("params");

        try {
            return switch (method) {
                case "initialize" -> initialize(id);
                case "tools/list" -> toolsList(id);
                case "tools/call" -> toolsCall(id, params);
                default -> errorResponse(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            return errorResponse(id, -32000, e.getMessage());
        }
    }

    private static ObjectNode initialize(JsonNode id) {
        ObjectNode result = JSON.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.putObject("capabilities").putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "phase6-trace-stats");
        serverInfo.put("version", "1.0.0");
        return okResponse(id, result);
    }

    private static ObjectNode toolsList(JsonNode id) {
        ArrayNode tools = JSON.createArrayNode();

        ObjectNode statsTool = tools.addObject();
        statsTool.put("name", "get_tool_stats");
        statsTool.put("description",
                "Per-tool betrouwbaarheidsstats uit ECHTE eerdere agent-runs: aantal calls, " +
                        "failureRate (denied/guardrail/error/gefaalde test), gemiddelde latency, " +
                        "en welk bronbestand die tool implementeert.");
        ObjectNode statsSchema = statsTool.putObject("inputSchema");
        statsSchema.put("type", "object");
        statsSchema.putObject("properties");

        ObjectNode failuresTool = tools.addObject();
        failuresTool.put("name", "get_recent_failures");
        failuresTool.put("description",
                "De meest recente niet-succesvolle trace-regels voor één tool (denied/guardrail/error/" +
                        "gefaalde test), met de originele detail-message.");
        ObjectNode failSchema = failuresTool.putObject("inputSchema");
        failSchema.put("type", "object");
        failSchema.putObject("properties").putObject("tool").put("type", "string");
        failSchema.putArray("required").add("tool");

        ObjectNode result = JSON.createObjectNode();
        result.set("tools", tools);
        return okResponse(id, result);
    }

    private static ObjectNode toolsCall(JsonNode id, JsonNode params) throws IOException {
        String name = params.path("name").asText();
        JsonNode args = params.path("arguments");

        String text = switch (name) {
            case "get_tool_stats" -> getToolStats();
            case "get_recent_failures" -> getRecentFailures(args.path("tool").asText());
            default -> throw new IllegalArgumentException("onbekende tool: " + name);
        };

        ObjectNode result = JSON.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        return okResponse(id, result);
    }

    private static List<JsonNode> readTraceLines() throws IOException {
        if (!Files.exists(TRACE_FILE)) return List.of();
        List<JsonNode> lines = new ArrayList<>();
        for (String raw : Files.readAllLines(TRACE_FILE)) {
            if (raw.isBlank()) continue;
            lines.add(JSON.readTree(raw));
        }
        return lines;
    }

    private static boolean isFailureSignal(JsonNode entry) {
        String status = entry.path("status").asText();
        if (!status.equals("ok")) return true; // error | denied | guardrail
        // run_tests geeft status "ok" terug ZODRA de tool zelf niet crashte -- ook
        // als de test binnenin faalde. Dat is een functionele mislukking, geen
        // tool-fout, maar wel relevant voor "hoe betrouwbaar is dit tool-pad".
        return entry.path("detail").asText().contains("\"passed\":false");
    }

    private static String getToolStats() throws IOException {
        List<JsonNode> entries = readTraceLines();
        Map<String, List<JsonNode>> byTool = new LinkedHashMap<>();
        for (JsonNode e : entries) {
            String tool = e.path("tool").asText();
            if (tool.equals("_loop_") || !TOOL_TO_FILE.containsKey(tool)) continue;
            byTool.computeIfAbsent(tool, k -> new ArrayList<>()).add(e);
        }

        ArrayNode out = JSON.createArrayNode();
        for (var entry : byTool.entrySet()) {
            List<JsonNode> calls = entry.getValue();
            long failures = calls.stream().filter(TraceStatsServer::isFailureSignal).count();
            double avgLatency = calls.stream().mapToLong(c -> c.path("latencyMs").asLong()).average().orElse(0);

            ObjectNode row = out.addObject();
            row.put("tool", entry.getKey());
            row.put("file", TOOL_TO_FILE.get(entry.getKey()));
            row.put("calls", calls.size());
            row.put("failures", failures);
            row.put("failureRate", calls.isEmpty() ? 0.0 : (double) failures / calls.size());
            row.put("avgLatencyMs", avgLatency);
        }
        return out.toString();
    }

    private static String getRecentFailures(String tool) throws IOException {
        List<JsonNode> entries = readTraceLines();
        ArrayNode out = JSON.createArrayNode();
        for (int i = entries.size() - 1; i >= 0 && out.size() < 5; i--) {
            JsonNode e = entries.get(i);
            if (!e.path("tool").asText().equals(tool)) continue;
            if (!isFailureSignal(e)) continue;
            ObjectNode row = out.addObject();
            row.put("ts", e.path("ts").asText());
            row.put("status", e.path("status").asText());
            row.put("detail", e.path("detail").asText());
        }
        return out.toString();
    }

    private static ObjectNode okResponse(JsonNode id, ObjectNode result) {
        ObjectNode resp = JSON.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        resp.set("result", result);
        return resp;
    }

    private static ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode resp = JSON.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        ObjectNode error = resp.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        return resp;
    }
}
