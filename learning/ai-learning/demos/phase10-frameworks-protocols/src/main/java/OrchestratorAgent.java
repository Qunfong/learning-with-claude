import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OrchestratorAgent — the concrete answer to phase7's self-identified gap
 * (see {@code phase7-multi-agent/README.md}, Open Question 3: "real A2A's
 * answer is Agent Cards served from a well-known URL — a runtime service
 * registry an orchestrator queries/watches").
 *
 * This replaces phase7's compile-time {@code OrchestratorAgent(A2AAgent
 * coder, A2AAgent reviewer)} constructor wiring with a runtime
 * {@code capability -> AgentCard} registry built by real
 * {@code GET <cardUrl>} calls + hand-rolled JSON parsing (see
 * {@link AgentCard}'s javadoc for why parsing is manual, not
 * Jackson-record-automatic).
 *
 * Three defenses, matching the three required test scenarios:
 * <ol>
 *   <li>{@link #discover} tolerates a malformed URL, a refused connection,
 *       a non-200 response, or a non-JSON/wrong-shaped body — logs and
 *       skips, never throws out of the method.</li>
 *   <li>Trusted-origin allowlist ({@link #trustedOrigins}): the ORIGIN of
 *       the URL actually being connected to (scheme+host+port) is checked
 *       BEFORE any network call is made — an untrusted origin is rejected
 *       without ever sending a request to it. This is AWS's Module 8 "agent
 *       card discovery from trusted sources only" defense.</li>
 *   <li>{@link #registry} is a live, mutable map: calling {@link #discover}
 *       again after startup — e.g. once a third agent comes online — adds
 *       to it. There is no "final wiring" step; the registry is always as
 *       fresh as the last poll.</li>
 * </ol>
 */
class OrchestratorAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Set<String> trustedOrigins;
    private final Map<String, AgentCard> registry = new ConcurrentHashMap<>();
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());

    OrchestratorAgent(Set<String> trustedOrigins) {
        this.trustedOrigins = trustedOrigins;
    }

    List<String> log() {
        return List.copyOf(log);
    }

    Map<String, AgentCard> registrySnapshot() {
        return Map.copyOf(registry);
    }

    Optional<AgentCard> resolve(String capability) {
        return Optional.ofNullable(registry.get(capability));
    }

    /**
     * One discovery poll against a single agent card URL. Never throws —
     * every failure mode is caught, logged, and treated as "skip this
     * agent," matching phase7's fallback philosophy
     * ({@code -Dphase7.mock=true}: keep the pipeline running rather than
     * crash on one bad dependency).
     */
    void discover(String cardUrl) {
        URI uri;
        try {
            uri = URI.create(cardUrl);
        } catch (IllegalArgumentException e) {
            log("REJECTED malformed card URL '" + cardUrl + "': " + e.getMessage());
            return;
        }
        if (uri.getHost() == null || uri.getScheme() == null) {
            log("REJECTED malformed card URL '" + cardUrl + "': missing scheme or host");
            return;
        }

        String origin = origin(uri);
        if (!trustedOrigins.contains(origin)) {
            log("REJECTED untrusted origin '" + origin + "' for card URL '" + cardUrl
                    + "' (not in allowlist " + trustedOrigins + ")");
            return;
        }

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log("UNREACHABLE '" + cardUrl + "': " + e.getClass().getSimpleName() + " — " + e.getMessage());
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("INTERRUPTED discovering '" + cardUrl + "'");
            return;
        }

        if (response.statusCode() != 200) {
            log("HTTP " + response.statusCode() + " from '" + cardUrl + "' — skipping");
            return;
        }

        AgentCard card;
        try {
            card = parseCard(response.body());
        } catch (Exception e) {
            log("MALFORMED card body from '" + cardUrl + "': " + e.getMessage());
            return;
        }

        for (String capability : card.capabilities()) {
            registry.put(capability, card);
        }
        log("DISCOVERED " + card + " via " + cardUrl);
    }

    void discoverAll(List<String> cardUrls) {
        cardUrls.forEach(this::discover);
    }

    private void log(String line) {
        log.add(line);
        System.out.println("[orchestrator] " + line);
    }

    private static String origin(URI uri) {
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port == -1 ? "" : ":" + port);
    }

    /** Hand-rolled JSON read — see {@link AgentCard}'s javadoc for why. */
    static AgentCard parseCard(String body) throws IOException {
        JsonNode node = JSON.readTree(body);
        if (!node.has("name") || !node.has("capabilities") || !node.has("endpoint")) {
            throw new IOException("card JSON missing required fields (name/capabilities/endpoint): " + body);
        }
        List<String> capabilities = new ArrayList<>();
        node.get("capabilities").forEach(c -> capabilities.add(c.asText()));
        return new AgentCard(node.get("name").asText(), capabilities, node.get("endpoint").asText());
    }
}
