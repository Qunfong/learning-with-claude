import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the phase-9 invariant the capstone's blast-radius claim rests on:
 * delegation narrows, never widens - as a hard check in the broker, not a
 * convention every call site has to remember.
 */
class CredentialDelegationTest {

    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");

    @Test
    void delegationCannotWidenScope() {
        CredentialBroker broker = new CredentialBroker();
        ScopedCredential parent = broker.mintRoot("EndUser", Set.of("read:incident"),
                Duration.ofMinutes(10), NOW);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> broker.delegate(parent, "SummarizerAgent",
                        Set.of("read:incident", "delete:incident"), Duration.ofMinutes(5), NOW));
        assertTrue(ex.getMessage().contains("delete:incident"),
                "the error must name the scope that was not held, got: " + ex.getMessage());
    }

    @Test
    void delegatedExpiryIsCappedAtTheParents() {
        CredentialBroker broker = new CredentialBroker();
        ScopedCredential parent = broker.mintRoot("EndUser", Set.of("read:incident"),
                Duration.ofMinutes(10), NOW);

        ScopedCredential child = broker.delegate(parent, "SummarizerAgent",
                Set.of("read:incident"), Duration.ofHours(8), NOW);

        assertEquals(parent.expiresAt(), child.expiresAt(),
                "an 8-hour request against a 10-minute parent must be capped, not honoured");
    }

    @Test
    void expiredCredentialCannotInvokeATool() {
        CredentialBroker broker = new CredentialBroker();
        ScopedCredential credential = broker.mintRoot("EndUser", Set.of("read:incident"),
                Duration.ofMinutes(1), NOW);
        Tool tool = new Tool("read_incident", "read it",
                Tool.oneStringParam("id", "incident id"), "read:incident",
                args -> "SHOULD NOT RUN");

        SecureToolInvoker.Invocation invocation = SecureToolInvoker.invoke(
                credential, tool, Tool.oneStringParam("id", "x"), NOW.plusSeconds(120));

        assertFalse(invocation.authorized());
        assertFalse(invocation.executed(), "the executor must not run for an expired credential");
        assertTrue(invocation.denialReason().contains("expired"));
    }

    @Test
    void scopeDenialHappensBeforeTheExecutorRuns() {
        CredentialBroker broker = new CredentialBroker();
        ScopedCredential credential = broker.mintRoot("EndUser", Set.of("read:incident"),
                Duration.ofMinutes(10), NOW);
        Tool tool = new Tool("send_email", "email it",
                Tool.oneStringParam("to", "recipient"), "send:email",
                args -> {
                    throw new AssertionError("executor must never be reached without the scope");
                });

        SecureToolInvoker.Invocation invocation = SecureToolInvoker.invoke(
                credential, tool, Tool.oneStringParam("to", "a@b.c"), NOW);

        assertFalse(invocation.authorized());
        assertFalse(invocation.executed());
    }

    @Test
    void theModelFacingToolSchemaNeverExposesTheRequiredScope() {
        Tool tool = new Tool("send_email", "email it",
                Tool.oneStringParam("to", "recipient"), "send:email", args -> "sent");

        String schema = tool.schema().toString();

        assertFalse(schema.contains("send:email"),
                "requiredScope must not appear in what a model is shown: " + schema);
        assertFalse(schema.contains("credential"), "no credential field may exist in the tool shape");
    }
}
