/**
 * Same interface as {@code phase7-multi-agent/src/main/java/A2AAgent.java}.
 * What changes in this module is not the interface but HOW an
 * OrchestratorAgent finds an implementation of it: phase7 wires
 * {@code CoderAgent}/{@code ReviewerAgent} into the orchestrator's
 * constructor at compile time. Here, each agent runs a real embedded
 * {@link com.sun.net.httpserver.HttpServer} and the orchestrator resolves
 * "who handles capability X" via a real HTTP GET against
 * {@code /.well-known/agent-card.json} at runtime — see
 * {@link OrchestratorAgent#discover}.
 */
interface A2AAgent {
    AgentCard card();
    TaskResult handle(Task task) throws Exception;
}
