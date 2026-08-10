import java.util.List;

/**
 * A2A Task Card (output side). Copied verbatim from
 * {@code phase7-multi-agent/src/main/java/TaskResult.java}.
 */
record TaskResult(String taskId, TaskState state, List<Artifact> artifacts, List<String> issues, String message) {

    static TaskResult done(String taskId, List<Artifact> artifacts, String message) {
        return new TaskResult(taskId, TaskState.DONE, artifacts, List.of(), message);
    }

    static TaskResult failed(String taskId, String message) {
        return new TaskResult(taskId, TaskState.FAILED, List.of(), List.of(), message);
    }
}
