import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part C mock — HTTP 402 Payment Required flow. See README for the
 * sequence diagram and the explicit "design-note + small mock, not a
 * subsystem" scope boundary (no Coinbase/blockchain/wallet integration).
 */
class X402Test {

    @Test
    void unpaidCallThrowsPaymentRequired() {
        MeteredTool tool = new MeteredTool();
        assertFalse(tool.isPaid());

        PaymentRequiredException ex = assertThrows(PaymentRequiredException.class, () -> tool.call("premium-search"));

        assertEquals(0.01, ex.amountDue());
        assertEquals("USD", ex.currency());
    }

    @Test
    void pipelinePausesForPaymentThenRetriesSuccessfully() {
        MeteredTool tool = new MeteredTool();

        String result = X402Pipeline.callWithPaymentRetry(tool, "premium-search");

        assertTrue(tool.isPaid());
        assertEquals("result for 'premium-search' (paid call)", result);
    }

    @Test
    void secondCallAfterPaymentDoesNotNeedToPayAgain() {
        MeteredTool tool = new MeteredTool();
        tool.authorizePayment(0.01, "USD");

        // no exception this time — already paid
        String result = tool.call("another-query");

        assertEquals("result for 'another-query' (paid call)", result);
    }
}
