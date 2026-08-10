import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Per-dependency circuit breaker -- NOT retry, see {@link Retry}'s header
 * javadoc for the explicit contrast it draws. {@link Retry} papers over ONE
 * flaky call by trying again a few times, right now. {@code CircuitBreaker}
 * watches a STREAK of consecutive failures across MANY calls to a named
 * dependency and, once the streak crosses a threshold, "opens": every
 * further call fails fast with {@link CircuitOpenException} -- no attempt is
 * made at all -- for a cooldown window. Once the cooldown elapses, exactly
 * one trial call is let through (half-open); success closes the breaker and
 * resets the streak, failure re-opens it and restarts the cooldown clock.
 *
 * Classic three-state machine:
 * <pre>
 *   CLOSED --(N consecutive failures)--&gt; OPEN
 *   OPEN --(cooldown elapses)--&gt; HALF_OPEN
 *   HALF_OPEN --(trial call succeeds)--&gt; CLOSED (streak reset)
 *   HALF_OPEN --(trial call fails)--&gt; OPEN (cooldown restarts)
 * </pre>
 *
 * One instance guards ONE named dependency -- {@link ResilientPipeline}
 * creates a separate breaker per agent hop, so a streak of failures in
 * CoderAgent can't trip PlannerAgent's breaker.
 */
class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    /** Thrown when the breaker is OPEN and the cooldown hasn't elapsed yet --
     * the call is refused before the wrapped action ever runs. */
    static class CircuitOpenException extends RuntimeException {
        CircuitOpenException(String message) {
            super(message);
        }
    }

    private final String name;
    private final int failureThreshold;
    private final Duration cooldown;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt = null;

    CircuitBreaker(String name, int failureThreshold, Duration cooldown) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
    }

    String name() {
        return name;
    }

    synchronized State state() {
        return state;
    }

    synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Runs {@code action} through the breaker.
     * <ul>
     *   <li>CLOSED: runs {@code action}; a failure increments the streak and
     *       opens the breaker once the streak reaches the threshold.</li>
     *   <li>OPEN, cooldown not elapsed: throws {@link CircuitOpenException}
     *       immediately -- {@code action} is never invoked.</li>
     *   <li>OPEN, cooldown elapsed: transitions to HALF_OPEN and runs
     *       {@code action} as the one trial call.</li>
     *   <li>HALF_OPEN: success closes the breaker and resets the streak;
     *       failure re-opens it and restarts the cooldown clock.</li>
     * </ul>
     */
    synchronized <T> T call(Supplier<T> action) {
        if (state == State.OPEN) {
            if (Duration.between(openedAt, Instant.now()).compareTo(cooldown) < 0) {
                throw new CircuitOpenException("circuit '" + name + "' is OPEN -- failing fast, no call attempted "
                        + "(cooldown ends at " + openedAt.plus(cooldown) + ")");
            }
            state = State.HALF_OPEN;
            System.out.println("  [circuit:" + name + "] cooldown elapsed -- HALF_OPEN, allowing one trial call");
        }

        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        if (state != State.CLOSED) {
            System.out.println("  [circuit:" + name + "] trial call succeeded -- CLOSED, failure streak reset");
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
    }

    private void onFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN) {
            System.out.println("  [circuit:" + name + "] trial call failed -- re-OPENING, cooldown restarts");
            open();
        } else if (consecutiveFailures >= failureThreshold) {
            System.out.println("  [circuit:" + name + "] " + consecutiveFailures
                    + " consecutive failures -- OPENING (cooldown " + cooldown + ")");
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = Instant.now();
    }
}
