import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic, model-free tests for the tool layer -- these are what
 * `mvn test` runs. DomainAgentDemo itself talks to a live Ollama model and is
 * a manual `exec:java` run (same split phase4-agents uses: RetryTest is the
 * only JUnit test there, CodingAgentDemo/GuardrailsDemo/etc. are all
 * live-model main-class runs, not JUnit tests).
 */
class SupportToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode refundArgs(String orderId, double amount) {
        ObjectNode n = JSON.createObjectNode();
        n.put("orderId", orderId);
        n.put("amount", amount);
        return n;
    }

    @Test
    void refundAtOrBelowCapIsAutoApproved() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool issueRefund = tools.issueRefund();

        String result = SupportTools.executeRaw(issueRefund, refundArgs("ORD-1001", 24.99));

        assertTrue(result.startsWith("APPROVED"), "expected auto-approval at/below the cap, got: " + result);
        assertEquals(1, tools.refundLog.size());
        assertTrue(tools.refundLog.get(0).startsWith("APPROVED"));
    }

    @Test
    void refundExactlyAtCapIsAutoApproved() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool issueRefund = tools.issueRefund();

        String result = SupportTools.executeRaw(issueRefund, refundArgs("ORD-2002", 100.00));

        assertTrue(result.startsWith("APPROVED"), "expected the cap itself to be inclusive, got: " + result);
    }

    /**
     * The load-bearing assertion for this whole phase: an over-cap refund is
     * denied by TOOL CODE, regardless of which agent (or system prompt) is
     * calling it -- this is what makes the guardrail a hard check rather than
     * something the model merely happens to respect.
     */
    @Test
    void refundAboveCapIsHardDeniedRegardlessOfCaller() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool issueRefund = tools.issueRefund();

        String result = SupportTools.executeRaw(issueRefund, refundArgs("ORD-2002", 500.00));

        assertTrue(result.startsWith("DENIED"), "expected a hard denial above the cap, got: " + result);
        assertTrue(result.contains("escalate_to_human"), "denial should point the caller at escalation");
        assertEquals(1, tools.refundLog.size());
        assertTrue(tools.refundLog.get(0).startsWith("DENIED"));
    }

    @Test
    void lookupOrderReturnsKnownOrder() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool lookupOrder = tools.lookupOrder();
        ObjectNode args = JSON.createObjectNode().put("orderId", "ORD-3003");

        String result = SupportTools.executeRaw(lookupOrder, args);

        assertTrue(result.contains("usb-c hub"), "expected mock order data, got: " + result);
        assertTrue(result.contains("39.50"));
    }

    @Test
    void lookupOrderReportsUnknownOrder() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool lookupOrder = tools.lookupOrder();
        ObjectNode args = JSON.createObjectNode().put("orderId", "ORD-9999");

        String result = SupportTools.executeRaw(lookupOrder, args);

        assertTrue(result.startsWith("FOUND_NONE"), "expected a not-found sentinel, got: " + result);
    }

    @Test
    void escalateToHumanLogsReason() {
        SupportTools tools = new SupportTools(DomainGuardrails.standard());
        Tool escalate = tools.escalateToHuman();
        ObjectNode args = JSON.createObjectNode().put("reason", "third contact about same issue");

        String result = SupportTools.executeRaw(escalate, args);

        assertTrue(result.startsWith("ESCALATED"));
        assertEquals(1, tools.escalationLog.size());
        assertEquals("third contact about same issue", tools.escalationLog.get(0));
    }

    @Test
    void perAgentToolInstancesDoNotShareLogs() {
        DomainGuardrails guardrails = DomainGuardrails.standard();
        SupportTools domainTools = new SupportTools(guardrails);
        SupportTools baselineTools = new SupportTools(guardrails);

        SupportTools.executeRaw(domainTools.escalateToHuman(), JSON.createObjectNode().put("reason", "domain-only"));

        assertEquals(1, domainTools.escalationLog.size());
        assertTrue(baselineTools.escalationLog.isEmpty(), "baseline's log must stay independent of domain's");
    }
}
