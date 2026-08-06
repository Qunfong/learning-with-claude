package fixture;

/**
 * Fixture standing in for the demo ticket's real target
 * ({@code OllamaClient.complete()}, see ../../README.md for why). Deliberately
 * BUGGY on purpose: {@link #complete} gives up on the very first HTTP 5xx
 * instead of retrying -- exactly what DEMO-001 asks the pipeline to fix.
 *
 * The {@link HttpCall} functional interface stands in for "make the HTTP
 * request and return the status code" so the accompanying test can simulate
 * transient 5xx responses without a real network call (deterministic,
 * fast, matches the pattern in {@code phase4-agents/CodingAgentDemo}'s
 * simulated transient failure).
 */
public class FixtureOllamaClient {

    /** Simulates issuing the HTTP request; returns the status code. */
    public interface HttpCall {
        int call() throws Exception;
    }

    /**
     * Executes {@code httpCall} and returns a canned "ok" response on success.
     *
     * BUG (the ticket to fix): a single 5xx response fails the whole call.
     * No retry, no backoff, no logging of the attempt.
     */
    public String complete(HttpCall httpCall) throws Exception {
        int status = httpCall.call();
        if (status >= 500) {
            throw new RuntimeException("HTTP " + status);
        }
        return "ok";
    }
}
