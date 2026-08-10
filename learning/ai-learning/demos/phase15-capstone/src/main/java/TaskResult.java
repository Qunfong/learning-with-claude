import java.util.List;

/**
 * Minimal re-implementation of {@code phase11-resilient-pipeline/TaskResult}
 * (itself descended from {@code phase7-multi-agent}) - copied in, not imported.
 *
 * <p>{@code confidence} is the field that makes abstention possible: without a
 * number attached to the handoff there is nothing for a gate to threshold on,
 * and "the agent seemed unsure" is not a control-flow condition.
 *
 * <p>Trimmed vs. phase 11: {@code artifacts} is a {@code List<String>} rather
 * than a list of typed {@code Artifact} records - this chain produces one text
 * artifact and the extra record would carry no information here.
 */
public record TaskResult(String taskId, String state, List<String> artifacts, List<String> issues,
                         String message, double confidence) {

    public static final List<String> VALID_STATES = List.of("DONE", "FAILED", "INPUT_REQUIRED");

    public TaskResult {
        artifacts = List.copyOf(artifacts);
        issues = List.copyOf(issues);
    }
}
