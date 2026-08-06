import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * PLANNING state's agent -- "Phase 4 loop + Phase 6 MCP to read files" per
 * spec. Reads the ticket's target file (via {@link LocalMcpFileServer}, this
 * phase's local MCP stand-in) and asks the model for a short, concrete plan.
 *
 * Simplification vs a full multi-turn phase4 AgentLoop: the file read here is
 * a single deterministic fetch, not something the model needs to decide to
 * call -- so this is one read (deterministic) + one reasoning call (the
 * model), rather than wiring up the full generic tool-calling engine for
 * exactly one always-called tool. See README.md for the full rationale.
 */
class PlannerAgent {

    private final OllamaClient client;
    private final String model;
    private final LocalMcpFileServer mcp;

    PlannerAgent(OllamaClient client, String model, LocalMcpFileServer mcp) {
        this.client = client;
        this.model = model;
        this.mcp = mcp;
    }

    record PlanResult(String plan, String existingContent, boolean fileExisted,
                       int tokensIn, int tokensOut, long latencyMs) {
    }

    PlanResult plan(Ticket ticket, String priorFeedback) {
        // "via MCP": resources/read, same method/response shape as phase6-mcp
        ObjectNode fileRead = mcp.call("resources/read", ticket.targetFile());
        String existing = fileRead.path("text").asText("");
        boolean exists = fileRead.path("exists").asBoolean(false);

        String system = """
                You are a senior Java planning agent inside an autonomous coding pipeline.
                Given a ticket and the CURRENT content of the target file, produce a SHORT,
                concrete implementation plan (3-6 bullet points, plain text, no markdown
                headers) describing exactly what code change will be made. Name the methods
                you will touch and the resulting behavior. Do not write code.
                """;

        StringBuilder user = new StringBuilder();
        user.append("TICKET ").append(ticket.id()).append(": ").append(ticket.title()).append('\n');
        user.append(ticket.description()).append("\n\n");
        user.append("TARGET FILE: ").append(ticket.targetFile())
                .append(" (exists=").append(exists).append(")\n---\n");
        user.append(exists && !existing.isBlank() ? existing : "(file does not exist yet -- new file)");
        user.append("\n---\n");
        if (priorFeedback != null && !priorFeedback.isBlank()) {
            user.append("\nHUMAN FEEDBACK ON THE PREVIOUSLY REJECTED PLAN (address this):\n")
                    .append(priorFeedback).append('\n');
        }

        long t0 = System.nanoTime();
        OllamaClient.ChatResult r = client.chat(model, system, user.toString());
        long ms = (System.nanoTime() - t0) / 1_000_000;

        return new PlanResult(r.text(), existing, exists, r.tokensIn(), r.tokensOut(), ms);
    }
}
