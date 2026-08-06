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
import java.util.stream.Stream;

/**
 * Fase 6 -- de CONSUMER-kant van het winkeltje-voorbeeld: leest de
 * bonnetjes die {@link ReceiptGeneratorServer} heeft weggeschreven en
 * beantwoordt vragen erover. Kent zelf NIETS van het menu of hoe een
 * bonnetje tot stand komt -- puur read + aggregate, zelfde scheiding als
 * McpServer (schrijft niets) vs. TraceStatsServer (leest wat een ANDER
 * proces al vastlegde).
 *
 * Draai apart: mvn -q exec:java -Dexec.mainClass=ReceiptAnalyticsServer
 */
public class ReceiptAnalyticsServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RECEIPTS_DIR = resolveReceiptsDir();

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

    private static Path resolveReceiptsDir() {
        Path here = Path.of("").toAbsolutePath();
        Path phase6Dir = here.endsWith("phase6-mcp") ? here : here.resolve("demos").resolve("phase6-mcp");
        return phase6Dir.resolve("receipts");
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
        serverInfo.put("name", "phase6-receipt-analytics");
        serverInfo.put("version", "1.0.0");
        return okResponse(id, result);
    }

    private static ObjectNode toolsList(JsonNode id) {
        ArrayNode tools = JSON.createArrayNode();

        ObjectNode allTool = tools.addObject();
        allTool.put("name", "get_all_receipts");
        allTool.put("description", "Lijst alle opgeslagen bonnetjes: id, tijdstip en totaalbedrag.");
        ObjectNode allSchema = allTool.putObject("inputSchema");
        allSchema.put("type", "object");
        allSchema.putObject("properties");

        ObjectNode summaryTool = tools.addObject();
        summaryTool.put("name", "get_spending_summary");
        summaryTool.put("description",
                "Geaggregeerde uitgaven over ALLE bonnetjes: aantal bonnetjes, totaal uitgegeven, " +
                        "en het meest gekochte artikel (op aantal).");
        ObjectNode summarySchema = summaryTool.putObject("inputSchema");
        summarySchema.put("type", "object");
        summarySchema.putObject("properties");

        ObjectNode itemTool = tools.addObject();
        itemTool.put("name", "get_item_spending");
        itemTool.put("description", "Hoeveel is er in totaal besteed aan ÉÉN specifiek artikel, en hoeveel keer gekocht.");
        ObjectNode itemSchema = itemTool.putObject("inputSchema");
        itemSchema.put("type", "object");
        itemSchema.putObject("properties").putObject("name").put("type", "string");
        itemSchema.putArray("required").add("name");

        ObjectNode result = JSON.createObjectNode();
        result.set("tools", tools);
        return okResponse(id, result);
    }

    private static ObjectNode toolsCall(JsonNode id, JsonNode params) throws IOException {
        String name = params.path("name").asText();
        JsonNode args = params.path("arguments");

        String text = switch (name) {
            case "get_all_receipts" -> getAllReceipts();
            case "get_spending_summary" -> getSpendingSummary();
            case "get_item_spending" -> getItemSpending(args.path("name").asText());
            default -> throw new IllegalArgumentException("onbekende tool: " + name);
        };

        ObjectNode result = JSON.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        return okResponse(id, result);
    }

    private static List<JsonNode> readAllReceipts() throws IOException {
        if (!Files.isDirectory(RECEIPTS_DIR)) return List.of();
        List<JsonNode> receipts = new ArrayList<>();
        try (Stream<Path> files = Files.list(RECEIPTS_DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                receipts.add(JSON.readTree(Files.readString(f)));
            }
        }
        receipts.sort(Comparator.comparing(r -> r.path("timestamp").asText()));
        return receipts;
    }

    private static String getAllReceipts() throws IOException {
        ArrayNode out = JSON.createArrayNode();
        for (JsonNode r : readAllReceipts()) {
            ObjectNode row = out.addObject();
            row.put("id", r.path("id").asText());
            row.put("timestamp", r.path("timestamp").asText());
            row.put("total", r.path("total").asDouble());
        }
        return out.toString();
    }

    private static String getSpendingSummary() throws IOException {
        List<JsonNode> receipts = readAllReceipts();
        double totalSpent = 0;
        Map<String, Integer> qtyPerItem = new LinkedHashMap<>();

        for (JsonNode r : receipts) {
            totalSpent += r.path("total").asDouble();
            for (JsonNode line : r.path("items")) {
                String itemName = line.path("name").asText();
                qtyPerItem.merge(itemName, line.path("qty").asInt(), Integer::sum);
            }
        }

        ObjectNode out = JSON.createObjectNode();
        out.put("receiptCount", receipts.size());
        out.put("totalSpent", Math.round(totalSpent * 100.0) / 100.0);

        Optional<Map.Entry<String, Integer>> top = qtyPerItem.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        out.put("mostPurchasedItem", top.map(Map.Entry::getKey).orElse(null));
        out.put("mostPurchasedQty", top.map(Map.Entry::getValue).orElse(0));
        return out.toString();
    }

    private static String getItemSpending(String itemName) throws IOException {
        String needle = itemName.toLowerCase();
        int totalQty = 0;
        double totalSpent = 0;
        for (JsonNode r : readAllReceipts()) {
            for (JsonNode line : r.path("items")) {
                if (!line.path("name").asText().equals(needle)) continue;
                totalQty += line.path("qty").asInt();
                totalSpent += line.path("lineTotal").asDouble();
            }
        }
        ObjectNode out = JSON.createObjectNode();
        out.put("item", needle);
        out.put("totalQty", totalQty);
        out.put("totalSpent", Math.round(totalSpent * 100.0) / 100.0);
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
