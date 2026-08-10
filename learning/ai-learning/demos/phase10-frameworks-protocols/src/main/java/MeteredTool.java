/**
 * Part C mock — a toy metered tool call that requires payment before the
 * first successful invocation, e.g. "$0.01 per premium search call." Real
 * x402 flows this through actual HTTP (402 status + {@code X-PAYMENT}
 * challenge header, client resubmits with a signed payment payload); here
 * it is a plain Java method throwing {@link PaymentRequiredException} to
 * keep the demo test-sized (see README "Part C" for the explicit scope
 * boundary — no Coinbase/blockchain/wallet integration).
 */
class MeteredTool {

    private boolean paid = false;

    /**
     * @throws PaymentRequiredException on every call until
     *                                  {@link #authorizePayment} has run once
     */
    String call(String query) {
        if (!paid) {
            throw new PaymentRequiredException(0.01, "USD");
        }
        return "result for '" + query + "' (paid call)";
    }

    /** Mock payment authorization — in real x402 this is a signed on-chain
     * USDC transfer the client attaches as proof-of-payment on retry. */
    void authorizePayment(double amount, String currency) {
        System.out.println("[mock-payment] authorized " + amount + " " + currency + " (no real transfer — x402 is mocked)");
        this.paid = true;
    }

    boolean isPaid() {
        return paid;
    }
}
