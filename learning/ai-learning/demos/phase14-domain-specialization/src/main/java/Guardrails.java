import com.fasterxml.jackson.databind.JsonNode;

/**
 * Copied unchanged from phase4-agents/Guardrails.java. These are the generic,
 * domain-agnostic ENGINE-level rails (loop mechanics) that apply equally to
 * the domain agent and the baseline agent in this phase's negative control —
 * they exist purely so neither demo agent can run away forever, they are not
 * the "domain guardrails" this phase is about. See {@link DomainGuardrails}
 * for the domain-specific rails (competitor-pricing refusal, refund cap).
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

    static Guardrails withCap(int maxIterations) {
        return new Guardrails(maxIterations, 0, true, null);
    }
}
