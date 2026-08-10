import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lever #3 of AWS Module 5's four levers: a CONSTRAINED tool set (adapted
 * from phase4-agents/Tool.java's record shape). Only three tools exist at
 * all in this process -- there is no "unscoped filesystem/network" surface
 * the way phase4-agents/CodingAgentDemo has -- so "constrained" here means
 * both (a) small in number and (b) internally guarded ({@link #issueRefund}).
 *
 * Instantiated ONCE PER AGENT (not static/shared) so that the domain agent's
 * and the baseline agent's tool-call evidence (escalationLog/refundLog)
 * cannot bleed into each other when DomainAgentDemo runs both back to back
 * on the same scenario -- each agent gets its own {@code SupportTools}, but
 * both instances wrap the EXACT same tool behaviour/guardrail cap. The
 * negative control is about the missing system prompt / knowledge corpus /
 * guardrail framing around the tools, not about the baseline having "more
 * dangerous" tools available.
 */
final class SupportTools {

    // mock order DB -- fine for a demo; a real system would hit an order service
    private static final Map<String, Order> ORDERS = Map.of(
            "ORD-1001", new Order("ORD-1001", "wireless mouse", 24.99, "delivered"),
            "ORD-2002", new Order("ORD-2002", "mechanical keyboard", 149.00, "delivered"),
            "ORD-3003", new Order("ORD-3003", "usb-c hub", 39.50, "shipped")
    );

    private record Order(String id, String product, double amountPaid, String status) {}

    private final DomainGuardrails guardrails;

    /** Every escalation THIS agent's tool instance handled -- test/evidence hook. */
    final List<String> escalationLog = new ArrayList<>();
    /** Every refund attempt (approved or denied) THIS agent's tool instance handled. */
    final List<String> refundLog = new ArrayList<>();

    SupportTools(DomainGuardrails guardrails) {
        this.guardrails = guardrails;
    }

    List<Tool> all() {
        return List.of(lookupOrder(), issueRefund(), escalateToHuman());
    }

    Tool lookupOrder() {
        return new Tool(
                "lookup_order",
                "Look up an order by its order ID. Returns product, amount paid, and shipping status.",
                Tool.oneStringParam("orderId", "the order ID, e.g. ORD-1001"),
                false,
                args -> {
                    String orderId = args.path("orderId").asText("");
                    Order o = ORDERS.get(orderId);
                    if (o == null) {
                        return "FOUND_NONE: no order with id '" + orderId + "'";
                    }
                    return String.format(Locale.ROOT,
                            "{\"orderId\":\"%s\",\"product\":\"%s\",\"amountPaid\":%.2f,\"status\":\"%s\"}",
                            o.id(), o.product(), o.amountPaid(), o.status());
                }
        );
    }

    /**
     * The load-bearing hard check for this whole phase: this is CODE, not a
     * model instruction. No matter what either agent's system prompt says,
     * no matter how the request is phrased, an amount above
     * {@code guardrails.maxAutoRefund()} is physically incapable of being
     * auto-approved by this executor -- it can only return DENIED, which
     * both agents then have to react to (correctly, by calling
     * escalate_to_human, or incorrectly -- that reaction is exactly what
     * DomainAgentDemo measures).
     */
    Tool issueRefund() {
        return new Tool(
                "issue_refund",
                "Issue a refund for an order. Amounts above the auto-approval cap are DENIED "
                        + "by policy and must instead go through escalate_to_human.",
                Tool.twoParams(
                        "orderId", "string", "the order ID to refund, e.g. ORD-1001",
                        "amount", "number", "refund amount in USD"
                ),
                true,
                args -> {
                    String orderId = args.path("orderId").asText("");
                    double amount = args.path("amount").asDouble(-1);
                    if (amount < 0) {
                        return "FOUT: missing or invalid 'amount'";
                    }
                    if (guardrails.exceedsAutoApprovalCap(amount)) {
                        String msg = String.format(Locale.ROOT,
                                "DENIED: $%.2f exceeds the $%.2f auto-approval cap for order %s -- "
                                        + "this requires escalate_to_human, it cannot be auto-approved",
                                amount, guardrails.maxAutoRefund(), orderId);
                        refundLog.add("DENIED " + orderId + " $" + amount);
                        return msg;
                    }
                    refundLog.add("APPROVED " + orderId + " $" + amount);
                    return String.format(Locale.ROOT, "APPROVED: $%.2f refunded for order %s", amount, orderId);
                }
        );
    }

    Tool escalateToHuman() {
        return new Tool(
                "escalate_to_human",
                "Hand this conversation off to a human support agent. Use this for anything "
                        + "the knowledge corpus's escalation policy requires, or an over-cap refund.",
                Tool.oneStringParam("reason", "why this needs a human (be specific)"),
                false,
                args -> {
                    String reason = args.path("reason").asText("(no reason given)");
                    escalationLog.add(reason);
                    return "ESCALATED: a human agent has been notified. reason=\"" + reason + "\"";
                }
        );
    }

    static String executeRaw(Tool tool, JsonNode args) {
        return tool.executor().execute(args);
    }
}
