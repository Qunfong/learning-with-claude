import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wraps {@code phase4-agents}-style tool invocation so credentials are
 * attached <b>out-of-band</b>: the (simulated) LLM's tool-call request is
 * just {@code {"tool": "send_email", "arguments": {...}}} — a capability
 * name and arguments, nothing else. It never contains a credential, and the
 * {@link ToolResult} handed back to the model never contains one either
 * (see {@link Guardrails#redactCredentialLeak}). The {@link ScopedCredential}
 * that authorizes the call is a parameter of {@link #invoke}, supplied by
 * the orchestration layer that's holding it in memory on the agent's
 * behalf — structurally identical to how a real MCP server or Lambda
 * resolver holds AWS credentials outside the model's context and only ever
 * lets the model name a tool, per AWS Module 8's "credential delivery
 * through the MCP server layer, never into the agent's context window."
 *
 * <p>Every invocation passes through {@link Guardrails#authorize} first —
 * {@link Tool.ToolExecutor#execute} never runs for an unauthorized call.
 */
class SecureToolInvoker {

    private final AuditLog audit;

    SecureToolInvoker(AuditLog audit) {
        this.audit = audit;
    }

    ToolResult invoke(Tool tool, ScopedCredential credential, JsonNode args) {
        Guardrails.AuthDecision decision = Guardrails.authorize(credential, tool, audit);
        if (decision != Guardrails.AuthDecision.OK) {
            String reason = decision == Guardrails.AuthDecision.EXPIRED
                    ? "credential expired at " + credential.expiresAt()
                    : "scope '" + tool.requiredScope() + "' not granted to " + credential.principal();
            return ToolResult.denied(tool.name(), reason);
        }

        String raw;
        try {
            raw = tool.executor().execute(args);
        } catch (RuntimeException e) {
            return ToolResult.error(tool.name(), e.getMessage());
        }

        String safe = Guardrails.redactCredentialLeak(raw, credential, audit);
        return ToolResult.ok(tool.name(), safe);
    }

    record ToolResult(String tool, Status status, String output) {
        enum Status { OK, DENIED, ERROR }

        static ToolResult ok(String tool, String output) {
            return new ToolResult(tool, Status.OK, output);
        }

        static ToolResult denied(String tool, String reason) {
            return new ToolResult(tool, Status.DENIED, reason);
        }

        static ToolResult error(String tool, String message) {
            return new ToolResult(tool, Status.ERROR, message);
        }
    }
}
