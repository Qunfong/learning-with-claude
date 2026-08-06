import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fase 6 -- de MCP-server zelf, "Option B" uit de spec: het protocol met de
 * hand geïmplementeerd (JSON-RPC 2.0, één JSON-object per regel op
 * stdin/stdout) i.p.v. de officiële MCP Java SDK. Dat is bewust: het punt van
 * deze fase is BEGRIJPEN wat een MCP-client/server elkaar sturen, niet een
 * library aanroepen die dat voor je verbergt.
 *
 * Deze server heeft ZELF geen kennis van bestanden/analyse -- hij is een
 * dunne vertaallaag naar {@link CodeAnalysisApplication}'s REST-API (poort
 * 8080). Dat scheidt "wat de service kan" (REST, testbaar zonder AI) van
 * "hoe een model erbij kan" (MCP, deze klasse).
 *
 * Transport: stdio. De agent (MCP-client, zie fase4's {@code McpClient})
 * start dit process en praat via zijn stdin/stdout -- geen netwerkpoort,
 * geen auth, één client per process. Prima voor lokaal leren; voor
 * productie zou je HTTP+SSE gebruiken (zie spec.md).
 */
public class McpServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REST_BASE = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
                case "resources/list" -> resourcesList(id);
                case "resources/read" -> resourcesRead(id, params);
                default -> errorResponse(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            return errorResponse(id, -32000, e.getMessage());
        }
    }

    // ---- initialize: handshake, adverteert wat deze server kan -------------
    private static ObjectNode initialize(JsonNode id) {
        ObjectNode result = JSON.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "phase6-code-analysis");
        serverInfo.put("version", "1.0.0");
        return okResponse(id, result);
    }

    // ---- tools/list: de 4 tools uit spec.md, met JSON-schema voor args -----
    private static ObjectNode toolsList(JsonNode id) {
        ArrayNode tools = JSON.createArrayNode();
        tools.add(toolDef("list_demo_files", "Lijst alle .java-bestanden in de demos-codebase (relatieve paden).", null));
        tools.add(toolDef("read_demo_file", "Lees de volledige inhoud van één bestand op naam.", "name"));
        tools.add(toolDef("analyze_file", "Analyseer één bestand: aantal regels, methodes en TODO's.", "name"));
        tools.add(toolDef("get_codebase_metrics", "Geaggregeerde metrics over de hele demos-codebase.", null));
        ObjectNode result = JSON.createObjectNode();
        result.set("tools", tools);
        return okResponse(id, result);
    }

    private static ObjectNode toolDef(String name, String description, String stringArgField) {
        ObjectNode tool = JSON.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        if (stringArgField != null) {
            props.putObject(stringArgField).put("type", "string");
            schema.putArray("required").add(stringArgField);
        }
        return tool;
    }

    // ---- tools/call: dispatch naar de REST-service, resultaat als text -----
    private static ObjectNode toolsCall(JsonNode id, JsonNode params) throws IOException, InterruptedException {
        String name = params.path("name").asText();
        JsonNode args = params.path("arguments");

        String text = switch (name) {
            case "list_demo_files" -> restGet("/files");
            case "read_demo_file" -> restGet("/files/" + requireArg(args, "name"));
            case "analyze_file" -> restPost("/analyze", "{\"name\":\"" + requireArg(args, "name") + "\"}");
            case "get_codebase_metrics" -> restGet("/metrics");
            default -> throw new IllegalArgumentException("onbekende tool: " + name);
        };

        ObjectNode result = JSON.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        return okResponse(id, result);
    }

    private static String requireArg(JsonNode args, String field) {
        JsonNode v = args.get(field);
        if (v == null || v.asText().isBlank()) {
            throw new IllegalArgumentException("verplicht argument '" + field + "' ontbreekt");
        }
        return v.asText();
    }

    // ---- resources: de demo://<phase>/** URI-ruimte uit spec.md ------------
    private static ObjectNode resourcesList(JsonNode id) {
        ArrayNode resources = JSON.createArrayNode();
        for (int phase = 0; phase <= 6; phase++) {
            ObjectNode r = resources.addObject();
            r.put("uri", "demo://phase" + phase + "/**");
            r.put("name", "Fase " + phase + " source files");
            r.put("mimeType", "text/x-java-source");
        }
        ObjectNode result = JSON.createObjectNode();
        result.set("resources", resources);
        return okResponse(id, result);
    }

    private static ObjectNode resourcesRead(JsonNode id, JsonNode params) throws IOException, InterruptedException {
        String uri = params.path("uri").asText();
        // demo://phase6-mcp/McpServer.java -> bestandsnaam is het laatste segment;
        // findByName in de REST-service zoekt 'm overal onder demos/ op
        String fileName = uri.substring(uri.lastIndexOf('/') + 1);
        String text = restGet("/files/" + fileName);

        ObjectNode result = JSON.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        ObjectNode entry = contents.addObject();
        entry.put("uri", uri);
        entry.put("mimeType", "text/x-java-source");
        entry.put("text", text);
        return okResponse(id, result);
    }

    // ---- rauwe REST-calls naar CodeAnalysisApplication ----------------------
    private static String restGet(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(REST_BASE + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return send(req);
    }

    private static String restPost(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(REST_BASE + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    private static String send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("code-analysis-service HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    // ---- JSON-RPC 2.0 envelope helpers --------------------------------------
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
