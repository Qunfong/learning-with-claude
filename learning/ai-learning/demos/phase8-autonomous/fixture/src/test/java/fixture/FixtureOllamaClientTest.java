package fixture;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Encodes DEMO-001's acceptance criteria as real, runnable tests:
 * "retry up to 3 times on HTTP 5xx with exponential backoff, log each retry".
 *
 * FAILS against the initial {@link FixtureOllamaClient} (no retry at all) --
 * this is what {@code TestRunner} sees on the pipeline's first CODING/TESTING
 * pass, and what CoderAgent's generated fix must make pass.
 */
class FixtureOllamaClientTest {

    @Test
    void shouldRetryUpToThreeTimes_whenServerReturns5xxThenRecovers() throws Exception {
        FixtureOllamaClient client = new FixtureOllamaClient();
        AtomicInteger attempts = new AtomicInteger(0);

        String result = client.complete(() -> {
            int attempt = attempts.incrementAndGet();
            return attempt < 3 ? 503 : 200; // fails twice, succeeds on the 3rd try
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get(), "should have attempted exactly 3 times (2 retries + success)");
    }

    @Test
    void shouldGiveUp_afterThreeFailedAttempts() {
        FixtureOllamaClient client = new FixtureOllamaClient();
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () -> client.complete(() -> {
            attempts.incrementAndGet();
            return 503; // always fails
        }));

        assertEquals(3, attempts.get(), "should stop after 3 attempts, not retry forever");
    }
}
