import java.util.List;

/**
 * A2A Task Card (output side): final {@link TaskState}, any output
 * {@link Artifact}s, and — specific to this demo, not part of the generic
 * spec shape — a flat {@code issues} list so ReviewerAgent's structured
 * feedback survives the hop back through OrchestratorAgent without being
 * buried inside a single free-text {@code message}.
 */
record TaskResult(String taskId, TaskState state, List<Artifact> artifacts, List<String> issues, String message) {

    static TaskResult done(String taskId, List<Artifact> artifacts, String message) {
        return new TaskResult(taskId, TaskState.DONE, artifacts, List.of(), message);
    }

    static TaskResult failed(String taskId, String message) {
        return new TaskResult(taskId, TaskState.FAILED, List.of(), List.of(), message);
    }
}
