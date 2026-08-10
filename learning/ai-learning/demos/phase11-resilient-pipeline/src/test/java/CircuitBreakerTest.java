import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {

    @Test
    void opensAfterConsecutiveFailuresAndThenFailsFastWithoutAttempting() {
        CircuitBreaker breaker = new CircuitBreaker("dep", 3, Duration.ofMinutes(1));
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> breaker.call(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("boom");
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertEquals(3, attempts.get());

        // breaker is OPEN and cooldown is long -- the next call must fail fast,
        // meaning the supplied action is never even invoked
        assertThrows(CircuitBreaker.CircuitOpenException.class, () -> breaker.call(() -> {
            attempts.incrementAndGet();
            return "should not run";
        }));
        assertEquals(3, attempts.get(), "breaker must not attempt the call while OPEN");
    }

    @Test
    void staysClosedBelowFailureThreshold() {
        CircuitBreaker breaker = new CircuitBreaker("dep", 3, Duration.ofMinutes(1));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class, () -> breaker.call(() -> {
                throw new RuntimeException("boom");
            }));
        }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(2, breaker.consecutiveFailures());
    }

    @Test
    void halfOpensAfterCooldownAndClosesOnSuccessfulTrialCall() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker("dep", 2, Duration.ofMillis(50));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class, () -> breaker.call(() -> {
                throw new RuntimeException("boom");
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        Thread.sleep(100); // let the 50ms cooldown genuinely elapse

        String result = breaker.call(() -> "recovered");
        assertEquals("recovered", result);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
    }

    @Test
    void halfOpenTrialFailureReopensAndRestartsCooldown() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker("dep", 1, Duration.ofMillis(50));

        assertThrows(RuntimeException.class, () -> breaker.call(() -> {
            throw new RuntimeException("boom");
        }));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        Thread.sleep(100);

        // trial call during HALF_OPEN also fails -- must re-open, not stay half-open
        assertThrows(RuntimeException.class, () -> breaker.call(() -> {
            throw new RuntimeException("still down");
        }));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // cooldown just restarted -- an immediate next call must fail fast, not retry
        assertThrows(CircuitBreaker.CircuitOpenException.class, () -> breaker.call(() -> "nope"));
    }
}
