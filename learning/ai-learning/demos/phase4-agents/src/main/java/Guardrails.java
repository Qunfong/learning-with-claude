import com.fasterxml.jackson.databind.JsonNode;

/**
 * De grenzen die een {@link AgentLoop} bewaakt. {@link #none()} betekent
 * "geen enkele grens gekozen" — dat is precies wat {@code AgentLoopDemo}
 * gebruikt om te laten zien wat er misgaat zonder guardrails.
 *
 * @param maxIterations  &lt;= 0 betekent geen cap
 * @param tokenBudget    &lt;= 0 betekent geen budget
 * @param loopDetection  stop bij twee identieke tool-aanroepen op rij
 * @param confirmHook    optioneel: gate voor destructieve tools (zie {@link Tool#destructive()})
 */
record Guardrails(int maxIterations, int tokenBudget, boolean loopDetection, ConfirmHook confirmHook) {

    interface ConfirmHook {
        boolean confirm(String toolName, JsonNode args);
    }

    static Guardrails none() {
        return new Guardrails(0, 0, false, null);
    }
}
