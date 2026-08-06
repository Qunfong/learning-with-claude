import java.util.function.Supplier;

/**
 * Retry-with-backoff for TRANSIENT failures at a single call boundary
 * (the Ollama HTTP call in {@link OllamaClient}) -- copied from
 * {@code phase4-agents/Retry.java} and kept self-contained per phase8's
 * "no cross-module imports" convention (every phase is an independent Maven
 * module, see ../phase4-agents/README.md).
 *
 * IMPORTANT -- do not confuse this with the pipeline's own RETRY state in
 * {@link AutonomousPipeline}: this class retries a single HTTP call that
 * threw a transient exception; the pipeline's RETRY state re-runs the whole
 * CODING/TESTING cycle (up to 3 times) because a TEST FAILED, which is a
 * completely different, much higher-level kind of "try again". Same word,
 * two different layers -- see README.md for the full explanation.
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

    /** Marks a failure explicitly as TRANSIENT (worth retrying) -- a permanent
     * failure (e.g. 4xx, missing required argument) must NEVER be wrapped in
     * this, or you waste retries on something that will never succeed. */
    static class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }

        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
