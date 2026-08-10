import java.util.List;

/**
 * A2A Task Card (output side) -- superset of phase7-multi-agent/TaskResult.java:
 * adds {@code confidence} (Gap 3, "Confidence scoring + abstention" -- the
 * Coder/Reviewer-equivalent agents emit a confidence score alongside their
 * output, in [0.0, 1.0]). This is the exact record {@link HandoffSchema}
 * validates against before the next agent (here: the pipeline acting as
 * dispatcher) is allowed to see it -- see {@link HandoffValidator}.
 */
record TaskResult(String taskId, TaskState state, List<Artifact> artifacts, List<String> issues,
                   String message, double confidence) {

    static TaskResult done(String taskId, List<Artifact> artifacts, String message, double confidence) {
        return new TaskResult(taskId, TaskState.DONE, artifacts, List.of(), message, confidence);
    }

    static TaskResult failed(String taskId, String message) {
        return new TaskResult(taskId, TaskState.FAILED, List.of(), List.of(), message, 0.0);
    }

    static TaskResult withConfidence(TaskResult r, double confidence) {
        return new TaskResult(r.taskId(), r.state(), r.artifacts(), r.issues(), r.message(), confidence);
    }
}
