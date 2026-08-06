import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simulates Phase 6's MCP {@code resources/read} response shape (compare
 * phase6-mcp/src/main/java/McpServer.java#resourcesRead) WITHOUT spinning up
 * a real MCP server subprocess + its REST backend (CodeAnalysisApplication
 * on port 8080) — this module is deliberately self-contained, with no
 * cross-module Maven dependency on phase6-mcp. In a real wiring,
 * OrchestratorAgent would hold an McpClient (phase4-agents/McpClient.java)
 * and send a JSON-RPC {@code resources/read} request to a running McpServer
 * over stdio; here the identical response ENVELOPE is built directly from a
 * local file read, so the shape of "reading via MCP" stays visible in the
 * output even though the transport underneath is not the real protocol. This
 * is the one deviation from spec.md called out explicitly in the README.
 */
final class McpStyleFileReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private McpStyleFileReader() {}

    static ObjectNode readResource(String uri, Path path) throws IOException {
        String text = Files.readString(path);
        ObjectNode entry = JSON.createObjectNode();
        entry.put("uri", uri);
        entry.put("mimeType", "text/x-java-source");
        entry.put("text", text);
        return entry;
    }
}
