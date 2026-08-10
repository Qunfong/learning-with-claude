import java.util.Map;

/**
 * A2A Task Card (input side) — the unit of work handed to an agent. Copied
 * verbatim from {@code phase7-multi-agent/src/main/java/Task.java}; this
 * module does not change the Task/TaskResult shape, only HOW an
 * OrchestratorAgent learns which endpoint to send a Task to (see
 * {@link OrchestratorAgent}).
 */
record Task(String id, String type, Map<String, String> input) {}
