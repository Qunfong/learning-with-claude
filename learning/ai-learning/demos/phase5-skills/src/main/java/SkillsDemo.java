import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Fase 5 — Skills & Domain Knowledge.
 *
 * Kernidee: een skill is gewoon een tekst-bestand dat als system-prompt
 * wordt geïnjecteerd. Geen framework, geen magie — alleen structured context.
 *
 * Demo:
 *   RUN A — ZONDER skill: model schrijft Java op eigen houtje
 *   RUN B — MET skill:    model schrijft Java volgens java-standards
 *
 * Zelfde taak, zelfde model, zelfde temperature.
 * Enig verschil: system-prompt (leeg vs skill.md inhoud).
 *
 * Token-cost sectie toont hoeveel extra tokens een skill kost over 1 uur
 * gebruik bij realistisch call-volume — zodat je begrijpt waarom skill-grootte
 * een architectuur-beslissing is, niet alleen een content-beslissing.
 *
 * Draai: mvn -q compile exec:java
 */
public class SkillsDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static final String MODEL = "qwen2.5-coder:7b";
    static final String SKILL_RELATIVE = "skills/java-standards/skill.md";
    static final Path SKILL_PATH = resolveSkillPath();

    // ------------------------------------------------------------------
    // "../../skills/..." only resolves if the JVM's working directory
    // happens to be demos/phase5-skills (true for `mvn exec:java` run
    // from that folder, false for `java -jar target/skills-1.0.0.jar`
    // or most IDE run configs). Resolve against the class's own
    // code-source location instead, so it works regardless of cwd.
    // ------------------------------------------------------------------
    static Path resolveSkillPath() {
        Path start;
        try {
            start = Path.of(SkillsDemo.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not determine class location", e);
        }

        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SKILL_RELATIVE);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not find " + SKILL_RELATIVE + " above " + start
                        + " — run from within the repo, or fix SKILL_RELATIVE.");
    }

    static final String CODING_TASK = """
            Schrijf een Java-klasse die een CSV-bestand regel voor regel leest,
            de header-regel overslaat, elke regel splitst op komma en de naam
            en leeftijd als een apart object opslaat in een lijst.
            Voeg foutafhandeling toe voor ontbrekende of ongeldige waarden.
            Geef alleen Java-code terug, geen uitleg.""";

    // -----------------------------------------------------------------------
    // Token tracking — accumuleer over alle calls
    // -----------------------------------------------------------------------

    static int totalTokensIn  = 0;
    static int totalTokensOut = 0;
    static int skillTokensIn  = 0;  // alleen calls MET skill

    record CallResult(String text, int tokensIn, int tokensOut) {}

    static CallResult chat(String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", MODEL);
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userMessage);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/chat"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root  = JSON.readTree(resp.body());
        int tokensIn   = root.path("prompt_eval_count").asInt(0);
        int tokensOut  = root.path("eval_count").asInt(0);
        String text    = root.path("message").path("content").asText("").strip();

        totalTokensIn  += tokensIn;
        totalTokensOut += tokensOut;
        if (systemPrompt != null && !systemPrompt.isBlank()) skillTokensIn += tokensIn;

        System.out.printf("[tokens in=%d uit=%d]%n", tokensIn, tokensOut);
        return new CallResult(text, tokensIn, tokensOut);
    }

    // -----------------------------------------------------------------------
    // Cost-projectie: toont impact van skill over 1 uur productie-gebruik
    // -----------------------------------------------------------------------

    static void printCostProjection(int skillChars, int tokensInWithSkill,
                                    int tokensInWithoutSkill) {
        // Realistische schatting: 1 token ≈ 4 tekens (Engels/code)
        int skillTokens = skillChars / 4;

        // Stel: een dev-team gebruikt de agent 30x per uur (elke 2 minuten een call)
        int callsPerHour = 30;

        long hourlyWithout = (long) tokensInWithoutSkill * callsPerHour;
        long hourlyWith    = (long) tokensInWithSkill    * callsPerHour;
        long overhead      = hourlyWith - hourlyWithout;

        // Claude Sonnet 4 pricing: $3 per miljoen input-tokens (als hosted referentie)
        double pricePerMillion = 3.0;
        double hourlyOverheadCost = (overhead / 1_000_000.0) * pricePerMillion;

        System.out.println("\n" + "═".repeat(65));
        System.out.println("TOKEN-COST VAN EEN SKILL — projectie over 1 uur");
        System.out.println("═".repeat(65));
        System.out.printf("Skill-grootte          : %d tekens ≈ %d tokens%n",
                skillChars, skillTokens);
        System.out.printf("Tokens IN zonder skill : %d per call%n", tokensInWithoutSkill);
        System.out.printf("Tokens IN met skill    : %d per call%n", tokensInWithSkill);
        System.out.printf("Overhead per call      : +%d tokens (+%.0f%%)%n",
                tokensInWithSkill - tokensInWithoutSkill,
                100.0 * (tokensInWithSkill - tokensInWithoutSkill) / Math.max(1, tokensInWithoutSkill));
        System.out.println();
        System.out.printf("Bij %d calls/uur:%n", callsPerHour);
        System.out.printf("  Tokens/uur zonder    : %,d%n", hourlyWithout);
        System.out.printf("  Tokens/uur met skill : %,d%n", hourlyWith);
        System.out.printf("  Overhead/uur         : +%,d tokens%n", overhead);
        System.out.printf("  Kosten overhead/uur  : $%.4f  (Claude Sonnet @ $3/M)%n",
                hourlyOverheadCost);
        System.out.println();
        System.out.println("ARCHITECTUURLES:");
        System.out.println("  Kleine skill  (<500 tokens) → verwaarloosbare overhead");
        System.out.println("  Middelgrote   (500-2000)    → meetbaar maar acceptabel");
        System.out.println("  Grote skill   (>2000)       → overweeg sub-skills per taaktype");
        System.out.println("  Alternatief: RAG de skill zelf — retrieve alleen relevante regels");
        System.out.println("═".repeat(65));
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {

        String skillContent = Files.readString(SKILL_PATH);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Phase 5 — Skills Demo                                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("Model : " + MODEL);
        System.out.println("Skill : " + SKILL_PATH.toAbsolutePath().normalize());
        System.out.println("Grootte skill : " + skillContent.length() + " tekens");
        System.out.println();
        System.out.println("Taak:");
        System.out.println(CODING_TASK);

        // ── RUN A: geen skill ──────────────────────────────────────────────
        System.out.println("\n" + "═".repeat(65));
        System.out.println("RUN A — ZONDER skill (geen system prompt)");
        System.out.println("═".repeat(65));
        long t0 = System.currentTimeMillis();
        CallResult runA = chat(null, CODING_TASK);
        System.out.printf("Tijd: %d ms%n%n", System.currentTimeMillis() - t0);
        System.out.println(runA.text());

        // ── RUN B: met skill ───────────────────────────────────────────────
        System.out.println("\n" + "═".repeat(65));
        System.out.println("RUN B — MET java-standards skill als system prompt");
        System.out.println("═".repeat(65));
        t0 = System.currentTimeMillis();
        CallResult runB = chat(skillContent, CODING_TASK);
        System.out.printf("Tijd: %d ms%n%n", System.currentTimeMillis() - t0);
        System.out.println(runB.text());

        // ── Analyse ────────────────────────────────────────────────────────
        System.out.println("\n" + "═".repeat(65));
        System.out.println("ANALYSE — welke R1–R10 regels volgt B die A mist?");
        System.out.println("═".repeat(65));

        String analyseTask = """
                Vergelijk de twee Java-implementaties hieronder.

                IMPLEMENTATIE A (zonder coding standards):
                ```java
                %s
                ```

                IMPLEMENTATIE B (met coding standards):
                ```java
                %s
                ```

                Benoem per regel (R1–R10) welke implementatie die volgt.
                Exact format, één regel per R:
                R1 — [A / B / beide / geen]: één zin.
                Wees beknopt. Geen inleiding, geen conclusie."""
                .formatted(runA.text(), runB.text());

        System.out.println("\nModel analyseert de diff...\n");
        CallResult analyse = chat(skillContent, analyseTask);
        System.out.println(analyse.text());

        // ── Cost-projectie ─────────────────────────────────────────────────
        printCostProjection(skillContent.length(), runB.tokensIn(), runA.tokensIn());

        System.out.println("\nTotaal deze sessie: in=" + totalTokensIn
                + "  uit=" + totalTokensOut
                + "  (skill-calls: in=" + skillTokensIn + ")");
    }
}
