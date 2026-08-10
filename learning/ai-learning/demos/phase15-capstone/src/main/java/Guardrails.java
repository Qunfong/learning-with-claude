import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Minimal re-implementation of {@code phase4-agents/Guardrails} - copied in
 * and trimmed to the two engine rails this chain can actually trip:
 * an iteration cap and (tool, args) loop detection.
 *
 * <p>Dropped vs. phase 4: token budget (no model is called here, so there are
 * no tokens to count) and the destructive-action confirm hook (no interactive
 * prompt in a deterministic demo).
 */
public final class Guardrails {

    private final int maxIterations;
    private final Set<String> seenCalls = new LinkedHashSet<>();

    public Guardrails(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int maxIterations() {
        return maxIterations;
    }

    /** @return null if the step is allowed, otherwise the reason it is not. */
    public String checkStep(int iteration, String tool, String argsJson) {
        if (iteration > maxIterations) {
            return "iteration cap reached (" + maxIterations + ")";
        }
        String fingerprint = tool + "|" + argsJson;
        if (!seenCalls.add(fingerprint)) {
            return "loop detected: (" + tool + ", " + argsJson + ") already attempted this run";
        }
        return null;
    }
}
