import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real A2A agent-card discovery over real HTTP sockets — every server here
 * is a live {@link AgentCardServer} bound to an OS-assigned localhost port,
 * and {@link OrchestratorAgent#discover} makes a genuine
 * {@link java.net.http.HttpClient} GET against it. Nothing in this test
 * class is mocked; the only "mock" in this module is Part C's payment flow
 * and CoderAgent/ReviewerAgent's task-handling bodies (see their javadoc),
 * neither of which this test touches.
 */
class AgentCardDiscoveryTest {

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable c : toClose) {
            c.close();
        }
    }

    private CoderAgent startCoder() {
        CoderAgent coder = new CoderAgent();
        coder.start();
        toClose.add(coder::stop);
        return coder;
    }

    private ReviewerAgent startReviewer() {
        ReviewerAgent reviewer = new ReviewerAgent();
        reviewer.start();
        toClose.add(reviewer::stop);
        return reviewer;
    }

    private TestWriterAgent startTestWriter() {
        TestWriterAgent agent = new TestWriterAgent();
        agent.start();
        toClose.add(agent::stop);
        return agent;
    }

    private RogueAgent startRogue() {
        RogueAgent rogue = new RogueAgent();
        rogue.start();
        toClose.add(rogue::stop);
        return rogue;
    }

    private static String originOf(String endpoint) {
        return URI.create(endpoint).toString();
    }

    // --- 1. dynamic discovery: a third agent registered AFTER the orchestrator
    // has already started and already polled once must be picked up on a
    // subsequent poll, without restarting or reconstructing the orchestrator. ---
    @Test
    void thirdAgentRegisteredAfterStartupIsDiscoveredOnRepoll() {
        CoderAgent coder = startCoder();
        ReviewerAgent reviewer = startReviewer();

        Set<String> trustedOrigins = new HashSet<>(Set.of(
                originOf(coder.card().endpoint()), originOf(reviewer.card().endpoint())));
        OrchestratorAgent orchestrator = new OrchestratorAgent(trustedOrigins);

        // orchestrator "already started" and already did one full poll
        orchestrator.discoverAll(List.of(coder.cardUrl(), reviewer.cardUrl()));
        assertTrue(orchestrator.resolve("code.generate").isPresent());
        assertTrue(orchestrator.resolve("test.write").isEmpty(), "third agent must not exist yet");

        // NOW a third agent comes online, after the orchestrator was constructed and already polled
        TestWriterAgent testWriter = startTestWriter();
        trustedOrigins.add(originOf(testWriter.card().endpoint()));

        orchestrator.discover(testWriter.cardUrl());

        assertTrue(orchestrator.resolve("test.write").isPresent(), "orchestrator must pick up the new agent on re-poll");
        assertEquals("TestWriterAgent", orchestrator.resolve("test.write").get().name());
        // the original two capabilities are still resolvable — re-poll doesn't clobber the registry
        assertTrue(orchestrator.resolve("code.generate").isPresent());
        assertTrue(orchestrator.resolve("code.review").isPresent());
    }

    // --- 2a. malformed card URL: caught and logged, orchestrator keeps running. ---
    @Test
    void malformedUrlIsHandledGracefully() {
        OrchestratorAgent orchestrator = new OrchestratorAgent(Set.of());

        orchestrator.discover("http://local host:1234/.well-known/agent-card.json");

        assertTrue(orchestrator.registrySnapshot().isEmpty());
        assertTrue(orchestrator.log().stream().anyMatch(l -> l.startsWith("REJECTED malformed")));
    }

    // --- 2b. unreachable card URL: connection failure is caught and logged, not thrown. ---
    @Test
    void unreachableUrlIsHandledGracefully() {
        Set<String> trustedOrigins = new HashSet<>(Set.of("http://localhost:1"));
        OrchestratorAgent orchestrator = new OrchestratorAgent(trustedOrigins);

        orchestrator.discover("http://localhost:1/.well-known/agent-card.json");

        assertTrue(orchestrator.registrySnapshot().isEmpty());
        assertTrue(orchestrator.log().stream().anyMatch(l -> l.startsWith("UNREACHABLE")));
    }

    // --- 2c. malformed JSON body: a server that responds 200 with garbage is
    // also caught and logged, not just outright-down servers. ---
    @Test
    void malformedJsonBodyIsHandledGracefully() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext(AgentCardServer.WELL_KNOWN_PATH, exchange -> {
            byte[] garbage = "{not valid json".getBytes();
            exchange.sendResponseHeaders(200, garbage.length);
            try (var os = exchange.getResponseBody()) {
                os.write(garbage);
            }
        });
        server.start();
        toClose.add(() -> server.stop(0));
        int port = server.getAddress().getPort();
        String cardUrl = "http://localhost:" + port + AgentCardServer.WELL_KNOWN_PATH;

        OrchestratorAgent orchestrator = new OrchestratorAgent(new HashSet<>(Set.of("http://localhost:" + port)));
        orchestrator.discover(cardUrl);

        assertTrue(orchestrator.registrySnapshot().isEmpty());
        assertTrue(orchestrator.log().stream().anyMatch(l -> l.startsWith("MALFORMED")));
    }

    // --- 3. trusted-origin allowlist: a real, live, well-formed card from an
    // origin NOT on the allowlist must be rejected without being merged into
    // the registry. ---
    @Test
    void untrustedOriginIsRejectedEvenWhenCardIsValid() {
        CoderAgent coder = startCoder();
        RogueAgent rogue = startRogue();

        // allowlist only trusts CoderAgent's origin — RogueAgent is real and reachable but not trusted
        OrchestratorAgent orchestrator = new OrchestratorAgent(new HashSet<>(Set.of(originOf(coder.card().endpoint()))));

        orchestrator.discover(coder.cardUrl());
        orchestrator.discover(rogue.cardUrl());

        assertTrue(orchestrator.resolve("code.generate").isPresent(), "trusted CoderAgent must still be discovered");
        assertEquals("CoderAgent", orchestrator.resolve("code.generate").get().name(),
                "RogueAgent (same capability, untrusted origin) must NOT have overwritten the trusted entry");
        assertTrue(orchestrator.log().stream().anyMatch(l -> l.startsWith("REJECTED untrusted origin")));
    }

    // --- 4. proof of a genuine network GET: fetch a live card over a real
    // socket with a plain HttpClient and check the raw JSON shape. ---
    @Test
    void cardIsServedAsRealJsonOverRealHttp() throws Exception {
        CoderAgent coder = startCoder();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(coder.cardUrl()))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("\"name\":\"CoderAgent\""));
        assertTrue(response.body().contains("\"code.generate\""));
        assertFalse(response.body().contains("in-process://"), "endpoint must be a real URL, not phase7's decorative string");
    }
}
