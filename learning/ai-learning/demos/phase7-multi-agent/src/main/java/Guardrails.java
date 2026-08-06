import com.fasterxml.jackson.databind.JsonNode;

/**
 * Copied from Phase 4 (demos/phase4-agents/src/main/java/Guardrails.java).
 * The bounds an {@link AgentLoop} enforces.
 *
 * @param maxIterations &lt;= 0 means no cap
 * @param tokenBudget   &lt;= 0 means no budget
 * @param loopDetection stop on two identical tool calls in a row
 * @param confirmHook   optional: gate for destructive tools (see {@link Tool#destructive()})
 */
record Guardrails(int maxIterations, int tokenBudget, boolean loopDetection, ConfirmHook confirmHook) {

    interface ConfirmHook {
        boolean confirm(String toolName, JsonNode args);
    }

    static Guardrails none() {
        return new Guardrails(0, 0, false, null);
    }
}
