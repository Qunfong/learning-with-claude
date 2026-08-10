/**
 * Part C (design-note + small mock, see README) — the Java-side shape of an
 * HTTP 402 Payment Required response from a metered tool, modeled on
 * Coinbase's x402 protocol (HTTP-based agent payments; see
 * {@code learning/ai-learning-gap-review/NOTES.md}'s AWS Module 2 summary:
 * "x402 (Coinbase's HTTP-based agent payment protocol, May 2025)"). This is
 * NOT a real x402 client — no wallet, no on-chain USDC transfer, no
 * {@code X-PAYMENT} header parsing. It exists only to give
 * {@link MeteredTool}/{@link X402Pipeline} a typed signal to catch, mirroring
 * how a real x402-aware HTTP client would branch on status 402 the same way
 * it already branches on 401/403.
 */
class PaymentRequiredException extends RuntimeException {

    private final double amountDue;
    private final String currency;

    PaymentRequiredException(double amountDue, String currency) {
        super("402 Payment Required: " + amountDue + " " + currency);
        this.amountDue = amountDue;
        this.currency = currency;
    }

    double amountDue() {
        return amountDue;
    }

    String currency() {
        return currency;
    }
}
