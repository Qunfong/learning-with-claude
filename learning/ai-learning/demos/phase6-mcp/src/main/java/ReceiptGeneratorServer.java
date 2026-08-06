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
import java.time.Instant;
import java.util.UUID;

/**
 * Fase 6 -- de "leuke" tweede voorbeeld-pair (naast code-analysis +
 * trace-stats): een winkeltje. Deze server is de PRODUCER: hij kent het
 * menu ({@link MenuCatalog}) en schrijft bonnetjes weg als JSON-bestand.
 * {@link ReceiptAnalyticsServer} is de CONSUMER -- leest diezelfde
 * bonnetjes terug en beantwoordt vragen erover. Twee servers, gescheiden
 * verantwoordelijkheid (schrijven vs. lezen/aggregeren), precies zoals
 * McpServer/TraceStatsServer dat deden voor code vs. runtime-gedrag.
 *
 * Draai apart: mvn -q exec:java -Dexec.mainClass=ReceiptGeneratorServer
 */
public class ReceiptGeneratorServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RECEIPTS_DIR = resolveReceiptsDir();

    public static void main(String[] args) throws IOException {
        Files.createDirectories(RECEIPTS_DIR);
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
        serverInfo.put("name", "phase6-receipt-generator");
        serverInfo.put("version", "1.0.0");
        return okResponse(id, result);
    }

    private static ObjectNode toolsList(JsonNode id) {
        ArrayNode tools = JSON.createArrayNode();

        ObjectNode menuTool = tools.addObject();
        menuTool.put("name", "list_menu");
        menuTool.put("description", "Toon alle artikelen in het winkeltje met hun prijs (excl. BTW) en het BTW-tarief.");
        ObjectNode menuSchema = menuTool.putObject("inputSchema");
        menuSchema.put("type", "object");
        menuSchema.putObject("properties");

        ObjectNode receiptTool = tools.addObject();
        receiptTool.put("name", "create_receipt");
        receiptTool.put("description",
                "Maak een bonnetje voor een lijst aankopen. Elk item moet een naam uit list_menu " +
                        "en een aantal (qty) hebben. Berekent subtotaal, BTW en totaal, en slaat het " +
                        "bonnetje op zodat get_all_receipts/get_spending_summary het later kunnen lezen.");
        ObjectNode receiptSchema = receiptTool.putObject("inputSchema");
        receiptSchema.put("type", "object");
        ObjectNode props = receiptSchema.putObject("properties");
        ObjectNode itemsProp = props.putObject("items");
        itemsProp.put("type", "array");
        ObjectNode itemSchema = itemsProp.putObject("items");
        itemSchema.put("type", "object");
        ObjectNode itemProps = itemSchema.putObject("properties");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("qty").put("type", "integer");
        itemSchema.putArray("required").add("name").add("qty");
        receiptSchema.putArray("required").add("items");

        ObjectNode result = JSON.createObjectNode();
        result.set("tools", tools);
        return okResponse(id, result);
    }

    private static ObjectNode toolsCall(JsonNode id, JsonNode params) throws IOException {
        String name = params.path("name").asText();
        JsonNode args = params.path("arguments");

        String text = switch (name) {
            case "list_menu" -> listMenu();
            case "create_receipt" -> createReceipt(args.path("items"));
            default -> throw new IllegalArgumentException("onbekende tool: " + name);
        };

        ObjectNode result = JSON.createObjectNode();
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text").put("text", text);
        return okResponse(id, result);
    }

    private static String listMenu() {
        ObjectNode out = JSON.createObjectNode();
        ObjectNode menu = out.putObject("menu");
        MenuCatalog.PRICES.forEach(menu::put);
        out.put("vatRate", MenuCatalog.VAT_RATE);
        return out.toString();
    }

    private static String createReceipt(JsonNode itemsArg) throws IOException {
        if (!itemsArg.isArray() || itemsArg.isEmpty()) {
            throw new IllegalArgumentException("'items' moet een niet-lege lijst zijn");
        }

        ObjectNode receipt = JSON.createObjectNode();
        String receiptId = "R-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 4);
        receipt.put("id", receiptId);
        receipt.put("timestamp", Instant.now().toString());

        ArrayNode lineItems = receipt.putArray("items");
        double subtotal = 0.0;
        for (JsonNode item : itemsArg) {
            String itemName = item.path("name").asText().toLowerCase();
            int qty = item.path("qty").asInt(0);
            Double unitPrice = MenuCatalog.PRICES.get(itemName);
            if (unitPrice == null) {
                throw new IllegalArgumentException(
                        "onbekend artikel '" + itemName + "' -- beschikbaar: " + MenuCatalog.PRICES.keySet());
            }
            if (qty <= 0) {
                throw new IllegalArgumentException("qty voor '" + itemName + "' moet > 0 zijn");
            }
            double lineTotal = round2(unitPrice * qty);
            subtotal += lineTotal;

            ObjectNode line = lineItems.addObject();
            line.put("name", itemName);
            line.put("qty", qty);
            line.put("unitPrice", unitPrice);
            line.put("lineTotal", lineTotal);
        }

        subtotal = round2(subtotal);
        double vat = round2(subtotal * MenuCatalog.VAT_RATE);
        double total = round2(subtotal + vat);
        receipt.put("subtotal", subtotal);
        receipt.put("vat", vat);
        receipt.put("total", total);

        Files.writeString(RECEIPTS_DIR.resolve(receiptId + ".json"), receipt.toPrettyString());
        return receipt.toString();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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
