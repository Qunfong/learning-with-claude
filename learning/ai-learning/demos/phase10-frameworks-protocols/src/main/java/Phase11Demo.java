import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Part A end-to-end demo: real HTTP agent-card discovery replacing phase7's
 * compile-time wiring. Run with {@code mvn -q compile exec:java}. Every
 * numbered step below corresponds to one of the four required test
 * scenarios from the task brief; the README's "What actually happened"
 * section is this program's captured stdout, not hypothetical prose.
 */
public class Phase11Demo {

    public static void main(String[] args) throws Exception {
        CoderAgent coder = new CoderAgent();
        ReviewerAgent reviewer = new ReviewerAgent();
        coder.start();
        reviewer.start();
        System.out.println("CoderAgent card server:    " + coder.cardUrl());
        System.out.println("ReviewerAgent card server: " + reviewer.cardUrl());

        // Allowlist starts with only the two agents we already trust — a mutable
        // Set so step 4 below can extend it, same as an ops team adding a newly
        // onboarded agent's origin to config at runtime.
        Set<String> trustedOrigins = new HashSet<>(Set.of(originOf(coder), originOf(reviewer)));
        OrchestratorAgent orchestrator = new OrchestratorAgent(trustedOrigins);

        System.out.println("\n=== Step 1: initial discovery (two known agents) ===");
        orchestrator.discoverAll(List.of(coder.cardUrl(), reviewer.cardUrl()));
        printRegistry(orchestrator);

        System.out.println("\n=== Step 2: malformed card URL (bad syntax) ===");
        orchestrator.discover("http://local host:1234/.well-known/agent-card.json");

        System.out.println("\n=== Step 3: unreachable card URL (nothing listening) ===");
        trustedOrigins.add("http://localhost:1"); // trust it so we prove UNREACHABLE, not REJECTED
        orchestrator.discover("http://localhost:1/.well-known/agent-card.json");

        System.out.println("\n=== Step 4: untrusted origin (real agent, not on allowlist) ===");
        RogueAgent rogue = new RogueAgent();
        rogue.start();
        try {
            System.out.println("RogueAgent card server:    " + rogue.cardUrl() + " (origin deliberately NOT added to allowlist)");
            orchestrator.discover(rogue.cardUrl());
        } finally {
            rogue.stop();
        }

        System.out.println("\n=== Step 5: dynamic discovery — third agent registered AFTER orchestrator started ===");
        System.out.println("resolve(\"test.write\") before third agent exists: " + orchestrator.resolve("test.write"));
        TestWriterAgent testWriter = new TestWriterAgent();
        testWriter.start();
        try {
            trustedOrigins.add(originOf(testWriter));
            System.out.println("TestWriterAgent card server: " + testWriter.cardUrl() + " (started post-startup, allowlist updated)");
            orchestrator.discover(testWriter.cardUrl());
            System.out.println("resolve(\"test.write\") after re-poll:       " + orchestrator.resolve("test.write"));
        } finally {
            testWriter.stop();
        }

        System.out.println("\n=== Final registry ===");
        printRegistry(orchestrator);

        coder.stop();
        reviewer.stop();

        System.out.println("\n=== Part C: x402 mock payment-required retry ===");
        MeteredTool tool = new MeteredTool();
        String result = X402Pipeline.callWithPaymentRetry(tool, "premium-search");
        System.out.println("final result: " + result);
    }

    private static String originOf(CoderAgent coder) {
        return java.net.URI.create(coder.card().endpoint()).toString();
    }

    private static String originOf(ReviewerAgent reviewer) {
        return java.net.URI.create(reviewer.card().endpoint()).toString();
    }

    private static String originOf(TestWriterAgent agent) {
        return java.net.URI.create(agent.card().endpoint()).toString();
    }

    private static void printRegistry(OrchestratorAgent orchestrator) {
        orchestrator.registrySnapshot().forEach((capability, card) ->
                System.out.println("  " + capability + " -> " + card));
    }
}
