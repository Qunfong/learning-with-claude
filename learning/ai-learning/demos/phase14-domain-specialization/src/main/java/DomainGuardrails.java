import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adapted from phase4-agents/Guardrails.java's pattern (a small immutable
 * record of deterministic rails), but these are DOMAIN guardrails — lever #4
 * of AWS Module 5's "four levers of domain specialization" (system prompt,
 * knowledge corpus, tool selection, guardrails — see
 * learning/ai-learning-gap-review/NOTES.md, Gap 7).
 *
 * Both checks here are deterministic CODE, not a hope that the model
 * behaves — same philosophy phase4-agents/README.md states explicitly for
 * GuardrailsDemo: "a guardrail is a system property (deterministic code),
 * not a gamble on model behaviour."
 *
 * - {@link #competitorPricingRefusal(String)} is applied BEFORE the domain
 *   agent's system prompt is even sent to the model, so the refusal is
 *   100% reproducible regardless of model mood.
 * - {@link #maxAutoRefund} is enforced a second time, independently, inside
 *   {@code SupportTools#issueRefund} itself -- so even if a future domain
 *   agent's prompt/guardrail here were bypassed, the tool layer still can't
 *   be talked into an over-cap auto-refund. Defense in depth, same lesson
 *   CodingAgentDemo's README draws about confirm-hooks vs. validation.
 */
record DomainGuardrails(double maxAutoRefund, List<String> competitorNames) {

    static DomainGuardrails standard() {
        return new DomainGuardrails(100.00, List.of("RivalCorp", "AcmeCompete", "ButlerBase"));
    }

    /**
     * Returns a logged refusal reason if the message asks us to compare/beat
     * a named competitor's pricing, empty otherwise. Pure string matching —
     * intentionally crude and intentionally not an LLM call, so this rail
     * fires the same way every single time.
     */
    Optional<String> competitorPricingRefusal(String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        boolean mentionsCompetitor = competitorNames.stream()
                .anyMatch(name -> lower.contains(name.toLowerCase(Locale.ROOT)));
        boolean mentionsPricingCompare = lower.contains("pricing") || lower.contains("price")
                || lower.contains("cheaper") || lower.contains("compare");
        if (mentionsCompetitor && mentionsPricingCompare) {
            return Optional.of("competitor-pricing guardrail: message references a named competitor "
                    + "(" + competitorNames.stream().filter(n -> lower.contains(n.toLowerCase(Locale.ROOT))).findFirst().orElse("?") + ") "
                    + "together with a pricing/comparison question -- refusing per domain policy "
                    + "(see KnowledgeCorpus 'Competitor comparisons' policy)");
        }
        return Optional.empty();
    }

    /** Hard cap check -- used by SupportTools#issueRefund, not just documentation. */
    boolean exceedsAutoApprovalCap(double amount) {
        return amount > maxAutoRefund;
    }
}
