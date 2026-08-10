import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ties {@link CircuitBreaker} to a real {@link A2AAgent} implementation
 * ({@link CoderAgent} in chaos mode -- see its class javadoc) rather than a
 * bare lambda, exercising the same wiring {@link ResilientPipeline}'s
 * scenario 3 demonstrates: a hard-down dependency trips the breaker after
 * {@code failureThreshold} consecutive failures, and the next call fails
 * fast without attempting the agent at all.
 */
class CircuitBreakerChaosIntegrationTest {

    @Test
    void chaosCoderAgentTripsBreakerThenSubsequentCallFailsFast() {
        // never actually reached over the network -- chaos mode throws before
        // any Ollama call is attempted, see CoderAgent.handle
        OllamaClient client = new OllamaClient();
        CoderAgent chaosCoder = new CoderAgent(client, true);
        CircuitBreaker breaker = new CircuitBreaker("coder-agent-chaos-test", 2, Duration.ofMinutes(1));

        Task task = new Task(UUID.randomUUID().toString(), "code.generate", Map.of("featureDescription", "x"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class, () -> breaker.call(() -> {
                try {
                    return chaosCoder.handle(task);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        assertThrows(CircuitBreaker.CircuitOpenException.class, () -> breaker.call(() -> {
            try {
                return chaosCoder.handle(task);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }
}
