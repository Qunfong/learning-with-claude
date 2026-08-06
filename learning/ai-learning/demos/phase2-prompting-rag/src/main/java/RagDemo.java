import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * Fase 2 — Demo 2: RAG over je eigen Java-codebase.
 *
 * KERNVRAAG: "Wat is de connectTimeout in de OllamaClient en waarom staat
 * stream op false?"  — specifiek genoeg dat het model zonder context faalt.
 *
 * 3 scenario's (zelfde vraag, zelfde model, andere context):
 *
 *   1. ZONDER RAG       → model weet het niet / hallucineert
 *   2. KLEINE CHUNKS    → 300 tekens per chunk, knipt midden in code
 *                         → soms juist, maar context ontbreekt
 *   3. SEMANTISCHE CHUNKS → split op lege regels (methode-blokken intact)
 *                          → accurate, compleet antwoord
 *
 * In-memory VectorStore: List<EmbeddedChunk> + cosine similarity.
 * Embedding via Ollama /api/embed met llama3.2:3b.
 *
 * Draai: mvn -q compile exec:java -Dexec.mainClass=RagDemo
 *
 * Let op: embedden kost tijd (≈2-5 s per chunk). Indexering drukt voortgang.
 */
public class RagDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static final String EMBED_MODEL = "llama3.2:3b";
    static final String LLM_MODEL   = "llama3.2:3b";

    // -----------------------------------------------------------------------
    // Data types
    // -----------------------------------------------------------------------

    record Chunk(String text, String source) {}

    record EmbeddedChunk(Chunk chunk, float[] vector) {}

    record Hit(Chunk chunk, double score) {}

    // -----------------------------------------------------------------------
    // In-memory VectorStore
    // -----------------------------------------------------------------------

    static final class VectorStore {
        private final List<EmbeddedChunk> store = new ArrayList<>();

        void index(List<Chunk> chunks) throws Exception {
            System.out.printf("  Indexeren: %d chunks", chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                float[] vec = embed(chunks.get(i).text());
                store.add(new EmbeddedChunk(chunks.get(i), vec));
                if ((i + 1) % 5 == 0 || i == chunks.size() - 1)
                    System.out.printf(" %d/%d", i + 1, chunks.size());
            }
            System.out.println(" ✓");
        }

        List<Hit> query(String queryText, int topK) throws Exception {
            float[] qVec = embed(queryText);
            return store.stream()
                    .map(ec -> new Hit(ec.chunk(), cosine(ec.vector(), qVec)))
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(topK)
                    .toList();
        }

        int size() { return store.size(); }
    }

    // -----------------------------------------------------------------------
    // Chunking strategieën — DIT is waar kwaliteitsverschil ontstaat
    // -----------------------------------------------------------------------

    /** Naive: vaste tekengrootte. Knipt dwars door methodes, comments, logica. */
    static List<Chunk> chunkBySize(String content, String source, int charSize) {
        List<Chunk> result = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + charSize, content.length());
            String text = content.substring(start, end).strip();
            if (text.length() > 20) result.add(new Chunk(text, source));
            start += charSize;
        }
        return result;
    }

    /**
     * Semantisch: split op lege regels.
     * Methode-blokken, commentaar en logische eenheden blijven intact.
     * Kleine fragmenten (<40 tekens) worden samengevoegd met volgende.
     */
    static List<Chunk> chunkBySemantic(String content, String source) {
        List<Chunk> result = new ArrayList<>();
        String[] blocks = content.split("\n\\s*\n+");
        StringBuilder buffer = new StringBuilder();

        for (String block : blocks) {
            String trimmed = block.strip();
            if (trimmed.isEmpty()) continue;

            buffer.append(trimmed).append("\n\n");

            // flush als buffer groot genoeg is (min 100, max 1200 tekens)
            if (buffer.length() >= 100) {
                result.add(new Chunk(buffer.toString().strip(), source));
                buffer.setLength(0);
            }
        }
        if (!buffer.isEmpty())
            result.add(new Chunk(buffer.toString().strip(), source));

        return result;
    }

    // -----------------------------------------------------------------------
    // HTTP: embedding + LLM
    // -----------------------------------------------------------------------

    static float[] embed(String text) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", EMBED_MODEL);
        body.put("input", text);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/embed"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Embed HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root = JSON.readTree(resp.body());
        JsonNode arr = root.path("embeddings").path(0);
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
        return vec;
    }

    static String complete(String context, String question) throws Exception {
        String userMsg = context.isBlank()
                ? question
                : "Gebruik uitsluitend de onderstaande code om de vraag te beantwoorden.\n\n"
                + "CODE-CONTEXT:\n" + context + "\n\nVRAAG: " + question;

        ObjectNode body = JSON.createObjectNode();
        body.put("model", LLM_MODEL);
        body.put("stream", false);
        body.putArray("messages")
                .addObject().put("role", "user").put("content", userMsg);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/chat"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + resp.body());

        return JSON.readTree(resp.body()).path("message").path("content").asText("").strip();
    }

    // -----------------------------------------------------------------------
    // Cosine similarity
    // -----------------------------------------------------------------------

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na  += (double) a[i] * a[i];
            nb  += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // -----------------------------------------------------------------------
    // Bestanden laden
    // -----------------------------------------------------------------------

    static Map<String, String> loadJavaFiles() throws IOException {
        // Pad relatief aan project-root van deze module (demos/phase2-prompting-rag/)
        Path demoRoot = Path.of("../..").toAbsolutePath().normalize();
        Map<String, String> files = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(demoRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !p.toString().contains("phase2")) // zichzelf overslaan
                 .sorted()
                 .forEach(p -> {
                     try {
                         files.put(demoRoot.relativize(p).toString(), Files.readString(p));
                     } catch (IOException e) {
                         System.err.println("Kan niet lezen: " + p);
                     }
                 });
        }
        return files;
    }

    // -----------------------------------------------------------------------
    // Output helpers
    // -----------------------------------------------------------------------

    static void printHits(List<Hit> hits) {
        System.out.println("  Opgehaalde chunks (top " + hits.size() + "):");
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            String preview = h.chunk().text().replace("\n", " ");
            if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
            System.out.printf("  [%d] score=%.3f | %s%n     %s%n",
                    i + 1, h.score(), h.chunk().source(), preview);
        }
    }

    static String buildContext(List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        for (Hit h : hits) {
            sb.append("// ").append(h.chunk().source()).append("\n");
            sb.append(h.chunk().text()).append("\n\n");
        }
        return sb.toString();
    }

    static void scenario(String label, List<Hit> hits, String question) throws Exception {
        System.out.println("\n" + "=".repeat(65));
        System.out.println(label);
        System.out.println("=".repeat(65));

        String context = "";
        if (!hits.isEmpty()) {
            printHits(hits);
            context = buildContext(hits);
            System.out.println();
        }

        long t0 = System.currentTimeMillis();
        String answer = complete(context, question);
        System.out.println("ANTWOORD:");
        System.out.println(answer);
        System.out.printf("[%d ms]%n", System.currentTimeMillis() - t0);
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String question = "Wat is de connectTimeout in de OllamaClient " +
                          "en waarom staat stream op false?";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  RAG Demo — Phase 2                                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("Vraag: \"" + question + "\"");
        System.out.println("Model: " + LLM_MODEL + "  |  Embed: " + EMBED_MODEL);

        // --- Bestanden laden ---
        Map<String, String> files = loadJavaFiles();
        System.out.println("\nGeladen bestanden: " + files.size());
        files.keySet().forEach(k -> System.out.println("  " + k));

        // ================================================================
        // 1. ZONDER RAG
        // ================================================================
        scenario("1. ZONDER RAG — model kent je codebase niet",
                List.of(), question);

        // ================================================================
        // 2. KLEINE CHUNKS (300 tekens) — naïeve strategie
        // ================================================================
        System.out.println("\n--- Indexering: kleine chunks (300 tekens) ---");
        VectorStore smallStore = new VectorStore();
        List<Chunk> smallChunks = new ArrayList<>();
        for (var e : files.entrySet())
            smallChunks.addAll(chunkBySize(e.getValue(), e.getKey(), 300));
        smallStore.index(smallChunks);

        List<Hit> smallHits = smallStore.query(question, 3);
        scenario("2. RAG + KLEINE CHUNKS (300 tekens) — knipt midden in code",
                smallHits, question);

        // ================================================================
        // 3. SEMANTISCHE CHUNKS — split op lege regels
        // ================================================================
        System.out.println("\n--- Indexering: semantische chunks (lege regels) ---");
        VectorStore semStore = new VectorStore();
        List<Chunk> semChunks = new ArrayList<>();
        for (var e : files.entrySet())
            semChunks.addAll(chunkBySemantic(e.getValue(), e.getKey()));
        semStore.index(semChunks);

        List<Hit> semHits = semStore.query(question, 3);
        scenario("3. RAG + SEMANTISCHE CHUNKS (per blok) — context intact",
                semHits, question);

        // ================================================================
        // Conclusie
        // ================================================================
        System.out.println("\n" + "═".repeat(65));
        System.out.println("VERGELIJKING:");
        System.out.printf("  Kleine chunks  : %d chunks, avg %d tekens%n",
                smallStore.size(),
                smallChunks.stream().mapToInt(c -> c.text().length()).sum() / Math.max(1, smallChunks.size()));
        System.out.printf("  Semantisch     : %d chunks, avg %d tekens%n",
                semStore.size(),
                semChunks.stream().mapToInt(c -> c.text().length()).sum() / Math.max(1, semChunks.size()));
        System.out.println();
        System.out.println("Les 1: Chunk-strategie bepaalt wat er in de context belandt.");
        System.out.println("Les 2: Te klein = context halverwege afgeknipt.");
        System.out.println("Les 3: Semantisch = methodegrenzen intact → betere retrieval.");
        System.out.println("Les 4: VectorStore is hier een List — productie gebruikt");
        System.out.println("       pgvector / Qdrant / Weaviate voor scale + persistentie.");
        System.out.println("═".repeat(65));
    }
}
