/** Copied from phase7-multi-agent/A2AAgent.java -- same "one interface, every
 * agent implements it" uniformity, kept here so PlannerAgent/CoderAgent/
 * ReviewerAgent/TesterAgent are drop-in-swappable by {@link ResilientPipeline}. */
interface A2AAgent {
    AgentCard card();

    TaskResult handle(Task task) throws Exception;
}
