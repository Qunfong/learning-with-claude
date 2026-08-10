import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainGuardrailsTest {

    @Test
    void refusesWhenCompetitorNamedWithPricingLanguage() {
        DomainGuardrails g = DomainGuardrails.standard();

        Optional<String> refusal = g.competitorPricingRefusal(
                "Is RivalCorp cheaper than you? Compare your pricing to theirs.");

        assertTrue(refusal.isPresent(), "expected a logged refusal reason");
        assertTrue(refusal.get().contains("RivalCorp"));
    }

    @Test
    void allowsCompetitorNameAloneWithoutPricingLanguage() {
        DomainGuardrails g = DomainGuardrails.standard();

        // mentions a competitor but isn't asking for a pricing comparison --
        // should NOT trip the guardrail, e.g. "I used to use RivalCorp's app"
        Optional<String> refusal = g.competitorPricingRefusal(
                "I used to use RivalCorp's app before switching to yours, love the redesign.");

        assertFalse(refusal.isPresent());
    }

    @Test
    void allowsPricingQuestionsWithoutNamingACompetitor() {
        DomainGuardrails g = DomainGuardrails.standard();

        Optional<String> refusal = g.competitorPricingRefusal("What's your standard pricing?");

        assertFalse(refusal.isPresent());
    }

    @Test
    void refundCapExactBoundaryIsNotExceeded() {
        DomainGuardrails g = DomainGuardrails.standard();

        assertFalse(g.exceedsAutoApprovalCap(100.00));
        assertTrue(g.exceedsAutoApprovalCap(100.01));
    }

    @Test
    void standardCapIsOneHundredDollars() {
        assertEquals(100.00, DomainGuardrails.standard().maxAutoRefund());
    }
}
