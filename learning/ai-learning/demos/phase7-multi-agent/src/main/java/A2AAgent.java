/**
 * Simplified A2A ("Option B" in spec.md): the same concepts as Google's real
 * A2A protocol (agent card / task / artifacts / states), but as a plain Java
 * interface called in-process instead of JSON-RPC over HTTP. Every agent in
 * this demo (CoderAgent, ReviewerAgent, and OrchestratorAgent itself)
 * implements this one interface — that uniformity is the point: swapping an
 * in-process call for a real HTTP POST later (Phase 8) only touches the
 * caller, never the agent's own logic.
 */
interface A2AAgent {
    AgentCard card();
    TaskResult handle(Task task) throws Exception;
}
