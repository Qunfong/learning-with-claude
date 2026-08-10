import java.util.List;

/** A2A Agent Card -- copied from phase7-multi-agent/AgentCard.java. */
record AgentCard(String name, List<String> capabilities, String endpoint) {
    @Override
    public String toString() {
        return name + " capabilities=" + capabilities + " endpoint=" + endpoint;
    }
}
