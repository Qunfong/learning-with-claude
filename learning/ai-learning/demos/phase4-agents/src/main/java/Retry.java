import java.util.function.Supplier;

/**
 * Retry-met-backoff voor TRANSIENTE fouten — de "error handling, retries"
 * bullet uit fase4 van het leerplan, expres apart van {@link Guardrails}:
 * guardrails bewaken de AGENT-loop (hoort bij {@link AgentLoop}), retries
 * horen bij een ENKELE call-grens (HTTP naar Ollama, of een tool-uitvoering)
 * en zijn de agent-loop niet eens zichtbaar — die ziet gewoon een resultaat
 * of, na uitgeputte retries, een fout.
 *
 * Fase1's README noemde dit expliciet als bewust weggelaten ("Geen
 * retries/backoff — een echte client heeft retry-logica nodig"). Dit is
 * waar die belofte wordt ingelost.
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
                long delay = baseDelayMs * (1L << (attempt - 1)); // exponentiële backoff
                System.out.printf("  [retry] poging %d/%d mislukt (%s) -- %dms wachten voor volgende poging%n",
                        attempt, maxAttempts, e.getMessage(), delay);
                sleep(delay);
            }
        }
        throw new RuntimeException("alle " + maxAttempts + " pogingen mislukt", lastFailure);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** Markeert een fout expliciet als TRANSIENT (de moeite van opnieuw proberen waard) — een
     * blijvende fout (bv. 4xx, ontbrekend verplicht argument) moet NOOIT hierin gewrapt worden,
     * anders verspil je retries aan iets dat nooit gaat lukken. */
    static class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
