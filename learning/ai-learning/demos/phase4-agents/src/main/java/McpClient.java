import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fase 6 -- de MCP-CLIENT-kant, gezien vanuit de agent. Dit is precies wat
 * "de agent praat MCP" in de praktijk betekent: een subprocess starten
 * (fase6's {@code McpServer}) en er JSON-RPC 2.0-regels mee uitwisselen over
 * stdin/stdout. Geen SDK, geen magie -- {@link ProcessBuilder} + een
 * request/response-regel per aanroep, zelfde discipline als
 * {@code OllamaClient}'s rauwe HTTP-wrapper.
 *
 * Synchroon van opzet (schrijf request, lees precies één antwoordregel) --
 * prima voor deze demo omdat {@code AgentLoop} tools ook synchroon aanroept.
 * Een echte MCP-client zou request-ids matchen tegen async binnenkomende
 * responses (de server kan in principe uit volgorde antwoorden).
 */
class McpClient implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AtomicInteger nextId = new AtomicInteger(1);

    private final Process process;
    private final BufferedReader stdout;
    private final PrintWriter stdin;

    /** Verbindt met het DEFAULT mainClass uit de MCP-server-pom (McpServer). */
    McpClient(String mcpServerPomPath) {
        this(mcpServerPomPath, null);
    }

    /** Verbindt met een SPECIFIEKE server-klasse in hetzelfde phase6-mcp-project --
     * zo kan één agent meerdere MCP-servers tegelijk als apart subprocess draaien
     * (bv. McpServer + TraceStatsServer, of ReceiptGeneratorServer + ReceiptAnalyticsServer). */
    McpClient(String mcpServerPomPath, String mainClassOverride) {
        try {
            // pb.directory() NAAR phase6-mcp/ zetten is bewust -- zonder dit erft het
            // subprocess de cwd van DEZE JVM (phase4-agents/), en TraceStatsServer /
            // ReceiptGeneratorServer / ReceiptAnalyticsServer resolven hun bestandspaden
            // (trace.jsonl, receipts/) relatief aan "waar draai ik vanuit". Verkeerde
            // cwd == stille lege resultaten, geen crash -- precies zo'n bug die je alleen
            // ziet als je de ECHTE tool-output controleert, niet de samenvatting van het model.
            java.io.File mcpProjectDir = java.nio.file.Path.of(mcpServerPomPath)
                    .toAbsolutePath().normalize().getParent().toFile();

            List<String> cmd = new ArrayList<>(List.of(
                    // mvn.cmd is een batch-bestand -- op Windows heeft ProcessBuilder een
                    // shell nodig om 'm te resolven, vandaar "cmd.exe /c" i.p.v. "mvn" direct
                    "cmd.exe", "/c", "mvn", "-q", "exec:java"));
            if (mainClassOverride != null) {
                cmd.add("-Dexec.mainClass=" + mainClassOverride);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(mcpProjectDir);
            pb.redirectErrorStream(false);
            this.process = pb.start();

            // build-fouten van Maven zelf gaan naar stderr -- apart pumpen naar
            // System.err zodat je die ziet, zonder de JSON-RPC-stdout te vervuilen
            Thread stderrPump = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = err.readLine()) != null) {
                        System.err.println("[mcp-server stderr] " + line);
                    }
                } catch (IOException ignored) {
                    // proces gestopt -- stream leeg, niets meer te pumpen
                }
            }, "mcp-stderr-pump");
            stderrPump.setDaemon(true);
            stderrPump.start();

            this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.stdin = new PrintWriter(new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            throw new RuntimeException("kon MCP-server niet starten (" + mcpServerPomPath + "): " + e.getMessage(), e);
        }

        initialize();
    }

    private void initialize() {
        ObjectNode params = JSON.createObjectNode();
        request("initialize", params);
    }

    /** Handig overload voor tools zonder argumenten (bv. list_demo_files). */
    String callTool(String toolName) {
        return callTool(toolName, JSON.createObjectNode());
    }

    /** Retourneert het "text"-veld uit het EERSTE content-blok -- exact wat een
     * MCP-tool-resultaat teruggeeft aan een LLM (platte tekst, geen structuur).
     * {@code arguments} wordt ONGEWIJZIGD doorgestuurd als het "arguments"-object
     * van de MCP-aanroep -- dit is precies wat {@link AgentLoop} als tool-argumenten
     * van het model doorgeeft, dus geen vertaalslag nodig ongeacht schema-vorm
     * (platte string, geneste array, ...). */
    String callTool(String toolName, JsonNode arguments) {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", (arguments == null || arguments.isMissingNode())
                ? JSON.createObjectNode() : arguments);

        JsonNode response = request("tools/call", params);
        JsonNode error = response.get("error");
        if (error != null) {
            return "FOUT: " + error.path("message").asText();
        }
        return response.path("result").path("content").path(0).path("text").asText();
    }

    private JsonNode request(String method, ObjectNode params) {
        ObjectNode req = JSON.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId.getAndIncrement());
        req.put("method", method);
        req.set("params", params);

        stdin.println(req.toString());
        stdin.flush();

        try {
            String responseLine = stdout.readLine();
            if (responseLine == null) {
                throw new IllegalStateException("MCP-server sloot stdout -- process waarschijnlijk gecrasht, zie stderr");
            }
            return JSON.readTree(responseLine);
        } catch (IOException e) {
            throw new RuntimeException("kon MCP-response niet lezen: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        process.destroy();
    }
}
