/**
 * Part C mock — the pipeline-pauses-for-payment-then-retries flow: call a
 * {@link MeteredTool}; on {@link PaymentRequiredException}, pause (here:
 * synchronously call a mock authorization step — a real implementation
 * would suspend the agent loop the same way {@code Guardrails}'
 * {@code confirmHook} in phase4-agents pauses for a human), then retry
 * exactly once. See README's sequence diagram for the full picture.
 */
class X402Pipeline {

    /**
     * @return the tool's result after a successful (possibly
     *         payment-gated) call
     */
    static String callWithPaymentRetry(MeteredTool tool, String query) {
        try {
            return tool.call(query);
        } catch (PaymentRequiredException e) {
            System.out.println("[x402] received 402 — " + e.getMessage() + " — pausing pipeline for payment authorization");
            tool.authorizePayment(e.amountDue(), e.currency());
            System.out.println("[x402] payment authorized — retrying call");
            return tool.call(query);
        }
    }
}
