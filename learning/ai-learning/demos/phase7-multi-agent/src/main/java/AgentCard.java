import java.util.List;

/**
 * A2A Agent Card — how an agent advertises itself (see spec.md). In real A2A
 * this is served over HTTP at a well-known URL and is how one agent
 * discovers another's capabilities before sending it work (a tiny service
 * registry). Option B (this module) is in-process, so there is no real
 * {@code endpoint} to hit — it's kept as a field anyway so the shape matches
 * the real protocol and the "how would this become real A2A" step (Phase 8)
 * is obvious: swap {@code endpoint} for a real URL and {@link A2AAgent#handle}
 * for an HTTP POST.
 */
record AgentCard(String name, List<String> capabilities, String endpoint) {
    @Override
    public String toString() {
        return name + " capabilities=" + capabilities + " endpoint=" + endpoint;
    }
}
