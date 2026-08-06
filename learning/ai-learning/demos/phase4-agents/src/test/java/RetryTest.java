import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {

    @Test
    void succeedsOnFirstAttemptWithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();
        String result = Retry.withBackoff(3, 1, () -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    void retriesAfterTransientFailureAndThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = Retry.withBackoff(3, 1, () -> {
            int attempt = calls.incrementAndGet();
            if (attempt < 2) {
                throw new Retry.TransientFailure("temporary hiccup");
            }
            return "recovered";
        });
        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    @Test
    void throwsAfterExhaustingAllAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                Retry.withBackoff(3, 1, () -> {
                    calls.incrementAndGet();
                    throw new Retry.TransientFailure("still failing");
                }));

        assertEquals(3, calls.get());
        assertEquals("alle 3 pogingen mislukt", ex.getMessage());
        assertEquals(Retry.TransientFailure.class, ex.getCause().getClass());
    }
}
