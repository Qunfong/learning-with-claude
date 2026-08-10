import java.util.List;

/**
 * A2A Agent Card — how an agent advertises itself. In phase7 this was a
 * decorative record: {@code endpoint} was a fake {@code in-process://...}
 * string because there was no real transport to hit. Here it is real: each
 * agent serves this exact shape as JSON from a live
 * {@code GET /.well-known/agent-card.json}, and {@code endpoint} is the real
 * base URL ({@code http://localhost:<port>}) another process would connect
 * to. This is the object that flows over the wire, so (de)serialization is
 * done by hand in {@link AgentCardServer} and {@link OrchestratorAgent}
 * rather than relying on Jackson's record support, since this repo's
 * compiler config does not set {@code -parameters} (see phase0's pom.xml
 * template) and Jackson needs that flag — or the parameter-names module,
 * which would be a new dependency — to deserialize records automatically.
 */
record AgentCard(String name, List<String> capabilities, String endpoint) {
    @Override
    public String toString() {
        return name + " capabilities=" + capabilities + " endpoint=" + endpoint;
    }
}
