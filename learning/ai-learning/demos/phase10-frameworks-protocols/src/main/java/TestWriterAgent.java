import java.util.List;

/**
 * The "third agent" for the dynamic-discovery proof (see README + task
 * brief requirement 1): capability {@code test.write}, started and
 * registered with the orchestrator strictly AFTER {@link Phase11Demo} has
 * already done its first discovery poll against {@link CoderAgent} and
 * {@link ReviewerAgent}. Structurally identical to CoderAgent/ReviewerAgent
 * — a thin wrapper around {@link AgentCardServer} — kept as its own class
 * (not a generic "MockAgent") so the demo output and tests read like real,
 * named agents rather than obviously-synthetic test fixtures.
 */
class TestWriterAgent {

    private final AgentCardServer cardServer = new AgentCardServer("TestWriterAgent", List.of("test.write"));

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
