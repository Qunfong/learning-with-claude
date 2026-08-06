import java.util.Map;

/**
 * A2A Task Card (input side) — the unit of work handed to an agent. Real A2A
 * carries a mutable {@code state} on the task itself as it moves through
 * submitted -> working -> done/failed; here that transition is represented
 * by the {@link TaskResult} an agent returns from {@link A2AAgent#handle},
 * since Option B is a single synchronous call rather than a polled resource.
 */
record Task(String id, String type, Map<String, String> input) {}
