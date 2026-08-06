import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Fase 6 -- de "leuke" tegenhanger van {@link McpAgentDemo}: dezelfde
 * {@link AgentLoop}, nu met twee MCP-servers uit `phase6-mcp/` die een
 * winkeltje simuleren:
 *   - ReceiptGeneratorServer  -- PRODUCER: kent het menu, boekt bonnetjes
 *   - ReceiptAnalyticsServer  -- CONSUMER: leest bonnetjes terug, aggregeert
 *
 * De agent doet in ÉÉN taak eerst een paar "aankopen" (schrijft naar server
 * A) en beantwoordt daarna een vraag over die aankopen (leest van server
 * B) -- zonder dat de twee servers ooit rechtstreeks met elkaar praten. De
 * agent is de enige schakel tussen schrijven en lezen.
 *
 * Vereist: `mvn -q compile` in phase6-mcp/ (voor exec:java's classpath).
 * Geen Spring Boot service nodig hier -- deze twee servers lezen/schrijven
 * rechtstreeks JSON-bestanden, geen REST-tussenlaag.
 *
 * Draai met: mvn -q compile exec:java -Dexec.mainClass=ReceiptAgentDemo
 */
public class ReceiptAgentDemo {

    static final String MCP_POM = "../phase6-mcp/pom.xml";
    static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        McpClient generator = new McpClient(MCP_POM, "ReceiptGeneratorServer");
        McpClient analytics = new McpClient(MCP_POM, "ReceiptAnalyticsServer");

        try {
            OllamaClient client = new OllamaClient();
            String model = "llama3.2:3b";

            Tool listMenu = new Tool(
                    "list_menu",
                    "Toon alle artikelen in het winkeltje met hun prijs (excl. BTW).",
                    Tool.emptyParams(),
                    false,
                    a -> generator.callTool("list_menu"));

            Tool createReceipt = new Tool(
                    "create_receipt",
                    "Maak één bonnetje voor een lijst aankopen. Elk item heeft 'name' (uit list_menu) en 'qty'.",
                    itemsArrayParams(),
                    false,
                    a -> generator.callTool("create_receipt", a));

            Tool allReceipts = new Tool(
                    "get_all_receipts",
                    "Lijst alle opgeslagen bonnetjes: id, tijdstip, totaalbedrag.",
                    Tool.emptyParams(),
                    false,
                    a -> analytics.callTool("get_all_receipts"));

            Tool spendingSummary = new Tool(
                    "get_spending_summary",
                    "Geaggregeerde uitgaven over ALLE bonnetjes: aantal, totaal, meest gekochte artikel.",
                    Tool.emptyParams(),
                    false,
                    a -> analytics.callTool("get_spending_summary"));

            Tool itemSpending = new Tool(
                    "get_item_spending",
                    "Totaal besteed en aantal gekocht van ÉÉN specifiek artikel.",
                    Tool.oneStringParam("name", "Artikelnaam, bv. 'koffie'."),
                    false,
                    a -> analytics.callTool("get_item_spending", a));

            AgentLoop loop = new AgentLoop(client, model,
                    "Je bent een winkel-assistent met toegang tot TWEE MCP-servers: één boekt bonnetjes " +
                            "(create_receipt, list_menu), de andere leest ze terug en rekent uitgaven uit " +
                            "(get_all_receipts/get_spending_summary/get_item_spending). Boek eerst alle gevraagde " +
                            "aankopen als aparte bonnetjes, beantwoord daarna de vraag met de analytics-tools.",
                    List.of(listMenu, createReceipt, allReceipts, spendingSummary, itemSpending),
                    new Guardrails(10, 0, true, null));

            System.out.println("=== Receipt-agent: boeken (server A) -> analyseren (server B) ===\n");

            String result = loop.run(
                    "Boek twee bonnetjes: (1) 3x koffie en 1x broodje, (2) 1x boek en 2x koffie. " +
                            "Geef daarna een overzicht: hoeveel heb ik in totaal uitgegeven, en wat heb ik het " +
                            "meest gekocht?");

            System.out.println("\neindresultaat: " + result);
        } finally {
            generator.close();
            analytics.close();
        }
    }

    // zelfde vorm als ReceiptGeneratorServer's create_receipt inputSchema --
    // een array van {name, qty}-objecten, geen platte string
    static ObjectNode itemsArrayParams() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode itemsProp = props.putObject("items");
        itemsProp.put("type", "array");
        ObjectNode itemSchema = itemsProp.putObject("items");
        itemSchema.put("type", "object");
        ObjectNode itemProps = itemSchema.putObject("properties");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("qty").put("type", "integer");
        itemSchema.putArray("required").add("name").add("qty");
        schema.putArray("required").add("items");
        return schema;
    }
}
