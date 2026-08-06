import java.util.function.Supplier;

/**
 * Retry-with-backoff for TRANSIENT failures. Copied from Phase 4
 * (demos/phase4-agents/src/main/java/Retry.java) — Phase 7 is a fully
 * self-contained Maven module (no cross-module dependency on phase4-agents),
 * so the handful of small classes it needs from the agent-loop engine are
 * reproduced here verbatim rather than imported. See that phase's README for
 * the original rationale: retries belong at a single call boundary (HTTP to
 * Ollama), not inside the agent loop itself, and only transient failures
 * (timeout, 5xx) are retried — a permanent failure (4xx, bad argument) is
 * never worth retrying.
 */
class Retry {

    private Retry() {}

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
                long delay = baseDelayMs * (1L << (attempt - 1)); // exponential backoff
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

    /** Marks a failure as explicitly TRANSIENT (worth retrying) — a permanent failure
     * (e.g. 4xx, missing required argument) must NEVER be wrapped in this, or you waste
     * retries on something that was never going to succeed. */
    static class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
