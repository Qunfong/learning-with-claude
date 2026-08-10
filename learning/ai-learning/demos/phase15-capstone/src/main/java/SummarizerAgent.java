import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The plan -> act -> observe agent of the capstone chain, in the shape of
 * {@code phase4-agents/AgentLoop} but with the model call replaced by a
 * deterministic decision table.
 *
 * <p><b>This is the honest boundary of this module.</b> A real agent's next
 * step comes out of a model; here it comes out of a {@code switch}. What that
 * preserves is the loop's <i>structure</i> - a step is proposed, guardrails
 * approve or reject it, a tool runs under a credential, the observation feeds
 * the next step, and the run ends by emitting a handoff payload that must
 * survive a schema check. What it does not preserve is the only genuinely hard
 * part of a real agent: that the proposed step might be nonsense. Every phase
 * that needs that (4, 7, 8, 14) calls a live model; this one deliberately does
 * not, so {@code mvn exec:java} works with nothing installed.
 *
 * @see CapstoneDemo for the chain this agent sits inside
 */
public final class SummarizerAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One proposed step. {@code terminal} means "stop looping and hand off". */
    public record PlanStep(String thought, String tool, ObjectNode args, boolean terminal) {
    }

    /** Behaviour flags, mirroring phase 11's constructor-parameter scenario switches. */
    public enum Mode {
        /** Confident, schema-valid handoff - the happy path. */
        NORMAL,
        /** Schema-valid but low-confidence handoff - trips the abstention gate. */
        LOW_CONFIDENCE,
        /** Well-intentioned but malformed handoff - trips the schema gate. */
        MALFORMED_HANDOFF
    }

    private final Mode mode;

    public SummarizerAgent(Mode mode) {
        this.mode = mode;
    }

    public String name() {
        return "SummarizerAgent";
    }

    /**
     * The deterministic stand-in for a model deciding what to do next.
     * Iteration 2 deliberately proposes a step the agent's credential does not
     * authorize - the interesting case, not an oversight.
     */
    public PlanStep plan(int iteration) {
        return switch (iteration) {
            case 1 -> new PlanStep(
                    "I cannot summarize an incident I have not read; fetch the record first.",
                    "read_incident", args("id", "INC-4471"), false);
            case 2 -> new PlanStep(
                    "The handover should reach the next on-call directly; try to email it.",
                    "send_email", args("to", "oncall@example.com"), false);
            default -> new PlanStep(
                    "Email was refused by my credential scope. Return the summary as an "
                            + "artifact and flag the unsent notification as an issue.",
                    null, null, true);
        };
    }

    /**
     * Builds the handoff payload as a raw JSON string - deliberately a String,
     * not a {@link TaskResult}, because that is what a model would actually
     * produce and what {@link HandoffValidator} exists to police.
     */
    public String buildHandoffJson(String taskId, List<String> observations, List<String> issues) {
        if (mode == Mode.MALFORMED_HANDOFF) {
            // Note the failure mode: a plausible-looking payload with confidence
            // as prose. This is exactly what a model does when it is asked for a
            // number and answers in words.
            ObjectNode bad = JSON.createObjectNode();
            bad.put("taskId", taskId);
            bad.put("state", "DONE");
            bad.putArray("artifacts").add(summary(observations));
            bad.putArray("issues");
            bad.put("message", "handover summary ready");
            bad.put("confidence", "fairly confident");
            return bad.toString();
        }

        double confidence = mode == Mode.LOW_CONFIDENCE ? 0.35 : 0.86;

        ObjectNode node = JSON.createObjectNode();
        node.put("taskId", taskId);
        node.put("state", "DONE");
        node.putArray("artifacts").add(summary(observations));
        ArrayNode issueArray = node.putArray("issues");
        issues.forEach(issueArray::add);
        if (mode == Mode.LOW_CONFIDENCE) {
            issueArray.add("the incident record does not say whether the connection-pool change "
                    + "was reverted everywhere or only in eu-west-1");
        }
        node.put("message", mode == Mode.LOW_CONFIDENCE
                ? "summary drafted, but I could not verify the scope of the rollback"
                : "handover summary ready");
        node.put("confidence", confidence);
        return node.toString();
    }

    private String summary(List<String> observations) {
        return "Handover for INC-4471: " + String.join(" | ", observations);
    }

    private static ObjectNode args(String field, String value) {
        ObjectNode node = JSON.createObjectNode();
        node.put(field, value);
        return node;
    }
}
