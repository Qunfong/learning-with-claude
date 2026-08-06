import java.util.List;

/**
 * Simplified in-process A2A shapes ("Option B" from
 * openspec/specs/phase7-multi-agent/spec.md), reimplemented locally per this
 * phase's brief -- NOT imported from demos/phase7-multi-agent/, which is a
 * separate, independent Maven module built concurrently. Kept conceptually
 * close to the phase7 interface:
 *
 * <pre>
 *     interface A2AAgent {
 *         AgentCard card();
 *         TaskResult handle(Task task) throws Exception;
 *     }
 * </pre>
 *
 * tailored down to exactly what {@link CoderAgent} and {@link ReviewerAgent}
 * need for this pipeline (no HTTP transport, no task-state machine beyond
 * done/failed -- this phase's state machine lives one level up, in
 * {@link AutonomousPipeline}).
 */
interface A2AAgent {
    AgentCard card();

    TaskResult handle(Task task) throws Exception;
}

/** How an agent advertises itself -- phase7's Agent Card concept. */
record AgentCard(String name, List<String> capabilities) {
}

/** The unit of work handed to an A2AAgent -- phase7's Task Card concept, trimmed to what's needed here. */
record Task(String type, String input, String context) {
}

/**
 * @param state    "done" | "failed" -- phase7's task state, trimmed to the
 *                 two outcomes this pipeline actually branches on
 * @param output   the artifact: generated code (CoderAgent) or review text (ReviewerAgent)
 * @param issues   structured findings (ReviewerAgent's rule violations; empty for CoderAgent)
 */
record TaskResult(String state, String output, List<String> issues,
                   int tokensIn, int tokensOut, long latencyMs) {
    boolean success() {
        return "done".equals(state);
    }
}
