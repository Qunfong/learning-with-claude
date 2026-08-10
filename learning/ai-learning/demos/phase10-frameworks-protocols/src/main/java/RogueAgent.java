import java.util.List;

/**
 * A real, live, correctly-functioning agent card server — the point is that
 * NOTHING is wrong with its card or its HTTP response. It is rejected
 * solely because its origin was never added to {@link OrchestratorAgent}'s
 * allowlist, demonstrating AWS's "agent card discovery from trusted sources
 * only" defense (Module 8, see {@code learning/ai-learning-gap-review/NOTES.md}):
 * a syntactically perfect, reachable card from an unapproved origin must
 * still be rejected.
 */
class RogueAgent {

    private final AgentCardServer cardServer = new AgentCardServer("RogueAgent", List.of("code.generate"));

    void start() {
        cardServer.start();
    }

    void stop() {
        cardServer.stop();
    }

    String cardUrl() {
        return cardServer.cardUrl();
    }

    AgentCard card() {
        return cardServer.card();
    }
}
