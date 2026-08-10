import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CredentialBrokerTest {

    @TempDir
    Path tempDir;

    private AuditLog audit;
    private CredentialBroker broker;

    @BeforeEach
    void setUp() {
        audit = new AuditLog(tempDir.resolve("audit.jsonl"));
        broker = new CredentialBroker(audit);
    }

    @AfterEach
    void tearDown() throws Exception {
        // nothing to clean up: @TempDir handles it
    }

    @Test
    void mintRoot_setsPrincipalChainAndScopes() {
        ScopedCredential cred = broker.mintRoot("EndUser", Set.of("read:customer"), Duration.ofMinutes(5));

        assertEquals(List.of("EndUser"), cred.principalChain());
        assertEquals("EndUser", cred.principal());
        assertEquals(Set.of("read:customer"), cred.grantedScopes());
        assertFalse(cred.isExpired(Instant.now()));
    }

    @Test
    void delegate_appendsToChain_andNarrowsScopes() {
        ScopedCredential root = broker.mintRoot("EndUser", Set.of("read:customer", "send:email"), Duration.ofMinutes(15));
        ScopedCredential orchestrator = broker.delegate(root, "OrchestratorAgent", Set.of("read:customer", "send:email"), Duration.ofMinutes(10));
        ScopedCredential specialized = broker.delegate(orchestrator, "SpecializedAgent", Set.of("send:email"), Duration.ofMinutes(5));

        assertEquals(List.of("EndUser", "OrchestratorAgent", "SpecializedAgent"), specialized.principalChain());
        assertEquals(Set.of("send:email"), specialized.grantedScopes());
    }

    @Test
    void delegate_rejectsScopesWiderThanParent() {
        ScopedCredential root = broker.mintRoot("EndUser", Set.of("read:customer"), Duration.ofMinutes(15));

        assertThrows(IllegalArgumentException.class,
                () -> broker.delegate(root, "OrchestratorAgent", Set.of("read:customer", "delete:account"), Duration.ofMinutes(5)));
    }

    @Test
    void delegate_capsExpiryAtParentExpiry() {
        ScopedCredential root = broker.mintRoot("EndUser", Set.of("read:customer"), Duration.ofSeconds(5));
        ScopedCredential child = broker.delegate(root, "OrchestratorAgent", Set.of("read:customer"), Duration.ofMinutes(30));

        assertFalse(child.expiresAt().isAfter(root.expiresAt()),
                "a delegated credential must never outlive its parent");
    }

    @Test
    void mint_writesAuditEntry() throws Exception {
        broker.mintRoot("EndUser", Set.of("read:customer"), Duration.ofMinutes(5));

        List<String> lines = Files.readAllLines(audit.path());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"mint\""));
        assertTrue(lines.get(0).contains("\"principal\":\"EndUser\""));
    }

    @Test
    void tokenFingerprint_neverExposesFullToken() {
        ScopedCredential cred = broker.mintRoot("EndUser", Set.of("read:customer"), Duration.ofMinutes(5));

        assertNotEquals(cred.token(), cred.tokenFingerprint());
        assertTrue(cred.tokenFingerprint().startsWith("***"));
        assertFalse(cred.tokenFingerprint().contains(cred.token()));
    }
}
