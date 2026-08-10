import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Real A2A Agent Card hosting: a tiny embedded {@link HttpServer} (JDK
 * built-in, {@code com.sun.net.httpserver} — zero new dependency, same
 * raw-{@code HttpClient} ethos the rest of this repo uses) that serves one
 * agent's {@link AgentCard} as JSON at the well-known path
 * {@code /.well-known/agent-card.json}. This is the concrete answer to
 * phase7's self-identified gap ("real A2A's answer is Agent Cards served
 * from a well-known URL").
 *
 * Bound to {@code localhost} with port 0 (OS-assigned free port) so multiple
 * agents — and multiple test runs in parallel — never collide; the actual
 * assigned port is read back via {@link HttpServer#getAddress()} and baked
 * into the card's own {@code endpoint} field before the server starts
 * accepting requests, so the card is always self-consistent.
 */
class AgentCardServer {

    static final String WELL_KNOWN_PATH = "/.well-known/agent-card.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final AgentCard card;

    AgentCardServer(String name, List<String> capabilities) {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not bind agent card server: " + e.getMessage(), e);
        }
        int port = server.getAddress().getPort();
        this.card = new AgentCard(name, capabilities, "http://localhost:" + port);
        server.createContext(WELL_KNOWN_PATH, this::serveCard);
        server.setExecutor(null); // default single-threaded executor is plenty for this demo
    }

    AgentCard card() {
        return card;
    }

    String endpoint() {
        return card.endpoint();
    }

    String cardUrl() {
        return card.endpoint() + WELL_KNOWN_PATH;
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    private void serveCard(HttpExchange exchange) throws IOException {
        byte[] body = toJson(card).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /** Hand-rolled JSON write — see {@link AgentCard}'s javadoc for why. */
    static String toJson(AgentCard card) {
        ObjectNode node = JSON.createObjectNode();
        node.put("name", card.name());
        ArrayNode caps = node.putArray("capabilities");
        card.capabilities().forEach(caps::add);
        node.put("endpoint", card.endpoint());
        return node.toString();
    }
}
