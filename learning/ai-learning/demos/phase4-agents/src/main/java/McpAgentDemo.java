import java.util.List;

/**
 * Fase 6 -- de agent-kant van MCP: dezelfde generieke {@link AgentLoop} uit
 * fase 4, maar nu met tools die GEEN lokale Java-code uitvoeren, maar via
 * {@link McpClient} JSON-RPC praten met TWEE losse MCP-server-processen uit
 * `phase6-mcp/`:
 *   - McpServer          -- kent de STATISCHE code (regels/methodes/TODO's)
 *   - TraceStatsServer   -- kent RUNTIME-gedrag (ECHTE trace.jsonl van fase4)
 *
 * Geen van beide servers kan alleen de vraag "welk bestand is het
 * risicovolst?" beantwoorden -- dat vereist BEIDE databronnen. De agent is
 * hier de enige partij die over twee servers heen redeneert, wat exact laat
 * zien waarom multi-server MCP meer oplevert dan één server: elke server
 * blijft klein en single-purpose, de COMBINATIE zit in de agent, niet in
 * een van de servers.
 *
 * Vereist: `mvn -q compile` in phase6-mcp/ (zodat exec:java z'n classpath
 * kan bouwen) en de Spring Boot service draaiend (`mvn spring-boot:run`
 * in phase6-mcp/, poort 8080) -- McpServer proxyt ernaartoe.
 *
 * Draai met: mvn -q compile exec:java -Dexec.mainClass=McpAgentDemo
 */
public class McpAgentDemo {

    static final String MCP_POM = "../phase6-mcp/pom.xml";

    public static void main(String[] args) {
        McpClient codeAnalysis = new McpClient(MCP_POM);                     // default mainClass: McpServer
        McpClient traceStats = new McpClient(MCP_POM, "TraceStatsServer");   // override: 2e server

        try {
            OllamaClient client = new OllamaClient();
            String model = "llama3.2:3b";

            Tool listFiles = new Tool(
                    "list_demo_files",
                    "Lijst alle .java-bestanden in de demos-codebase (relatieve paden).",
                    Tool.emptyParams(),
                    false,
                    a -> codeAnalysis.callTool("list_demo_files"));

            Tool analyzeFile = new Tool(
                    "analyze_file",
                    "Analyseer één bestand op naam: aantal regels, methodes en TODO's.",
                    Tool.oneStringParam("name", "Bestandsnaam, bv. 'AgentLoop.java'."),
                    false,
                    a -> codeAnalysis.callTool("analyze_file", a));

            Tool metrics = new Tool(
                    "get_codebase_metrics",
                    "Geaggregeerde metrics (regels/methodes/TODO's) over de HELE demos-codebase.",
                    Tool.emptyParams(),
                    false,
                    a -> codeAnalysis.callTool("get_codebase_metrics"));

            Tool toolStats = new Tool(
                    "get_tool_stats",
                    "Per-tool betrouwbaarheidsstats uit ECHTE eerdere agent-runs (trace.jsonl): " +
                            "aantal calls, failureRate, gemiddelde latency, en welk bronbestand die tool implementeert.",
                    Tool.emptyParams(),
                    false,
                    a -> traceStats.callTool("get_tool_stats"));

            Tool recentFailures = new Tool(
                    "get_recent_failures",
                    "De meest recente niet-succesvolle trace-regels voor één specifieke tool-naam.",
                    Tool.oneStringParam("tool", "Tool-naam uit get_tool_stats, bv. 'run_tests'."),
                    false,
                    a -> traceStats.callTool("get_recent_failures", a));

            AgentLoop loop = new AgentLoop(client, model,
                    "Je bent een agent met toegang tot TWEE MCP-servers: één kent de broncode van deze " +
                            "codebase (list_demo_files/analyze_file/get_codebase_metrics), de andere kent ECHTE " +
                            "runtime-betrouwbaarheidsdata uit eerdere agent-runs (get_tool_stats/get_recent_failures). " +
                            "Gebruik BEIDE om te bepalen welk bestand het risicovolst is: combineer code-complexiteit " +
                            "(methodes/regels) met betrouwbaarheid (failureRate). Rapporteer kort en concreet.",
                    List.of(listFiles, analyzeFile, metrics, toolStats, recentFailures),
                    new Guardrails(8, 0, true, null));

            System.out.println("=== MCP-agent: code-complexiteit + runtime-betrouwbaarheid combineren ===\n");

            String result = loop.run(
                    "Roep get_tool_stats aan. Bepaal welke tool de hoogste failureRate heeft. Zoek op in welk " +
                            "bestand die tool zit (het 'file'-veld), en roep analyze_file aan op dat bestand. " +
                            "Geef daarna een korte risico-conclusie: is dit bestand risicovol omdat het complex " +
                            "ÉN onbetrouwbaar is, of is één van de twee de hoofdreden?");

            System.out.println("\neindresultaat: " + result);
        } finally {
            codeAnalysis.close();
            traceStats.close();
        }
    }
}
