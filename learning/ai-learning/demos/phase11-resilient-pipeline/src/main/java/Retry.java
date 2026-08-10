import java.util.function.Supplier;

/**
 * Retry-with-backoff for a single TRANSIENT call -- copied from
 * phase8-autonomous/Retry.java (self-contained per this repo's "no
 * cross-module imports" convention). Used only inside {@link OllamaClient}'s
 * raw HTTP call.
 *
 * IMPORTANT -- this is NOT the circuit breaker (see {@link CircuitBreaker}'s
 * header comment for the distinction the phase spec calls out explicitly):
 * Retry papers over one flaky call by trying again a few times immediately.
 * CircuitBreaker watches a STREAK of failures across many calls and, once
 * tripped, stops calling out at all for a cooldown window -- opposite
 * instincts (retry = "try harder right now", breaker = "stop hammering a
 * dependency that's clearly down").
 */
class Retry {

    private Retry() {
    }

    static <T> T withBackoff(int maxAttempts, long baseDelayMs, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (TransientFailure e) {
                lastFailure = e;
                if (attempt == maxAttempts) {
                    break;
                }
                long delay = baseDelayMs * (1L << (attempt - 1));
                System.out.printf("  [retry] attempt %d/%d failed (%s) -- waiting %dms before next attempt%n",
                        attempt, maxAttempts, e.getMessage(), delay);
                sleep(delay);
            }
        }
        throw new RuntimeException("all " + maxAttempts + " attempts failed", lastFailure);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }

        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
