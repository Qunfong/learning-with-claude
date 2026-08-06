import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal, IN-PROCESS stand-in for "PlannerAgent reads files via MCP" (spec:
 * "Phase 6 MCP to read files"). Reimplemented locally and simplified: same
 * JSON-RPC-2.0-shaped {@code resources/read} method/response shape as
 * {@code phase6-mcp/McpServer.java#resourcesRead}, but called directly
 * in-process instead of over a subprocess/stdio transport.
 *
 * This is a deliberate simplification, not an oversight: phase6 already
 * teaches "what does a real MCP client/server exchange look like" (subprocess
 * + newline-delimited JSON-RPC, see phase6-mcp/README.md); phase8's teaching
 * point is the ORCHESTRATION layer built on top (checkpoints, gates,
 * observability), so re-proving MCP transport here would add moving parts
 * without adding to what this phase is about. The method/response shape is
 * kept identical so the concept transfers directly.
 *
 * Sandboxing: every path is resolved against {@link #root} and verified to
 * stay inside it before any read -- same pattern as
 * phase4-agents/CodingAgentDemo.resolveSafe.
 */
class LocalMcpFileServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;

    LocalMcpFileServer(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Mirrors phase6-mcp's McpServer#resourcesRead JSON-RPC method/response shape. */
    ObjectNode call(String method, String relPath) {
        if (!"resources/read".equals(method)) {
            throw new IllegalArgumentException("unsupported MCP method: " + method);
        }
        Path resolved = root.resolve(relPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(
                    "denied: path '" + relPath + "' escapes sandbox root " + root);
        }

        boolean exists = Files.exists(resolved);
        String text = "";
        if (exists) {
            try {
                text = Files.readString(resolved);
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read " + relPath, e);
            }
        }

        ObjectNode response = JSON.createObjectNode();
        response.put("uri", "demo://phase8-autonomous/" + relPath);
        response.put("exists", exists);
        response.put("text", text);
        return response;
    }

    /** Writes a file, creating parent directories as needed. Used by CODING, sandboxed the same way. */
    void write(String relPath, String content) {
        Path resolved = root.resolve(relPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(
                    "denied: path '" + relPath + "' escapes sandbox root " + root);
        }
        try {
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + relPath, e);
        }
    }

    Path resolve(String relPath) {
        return root.resolve(relPath).normalize();
    }
}
