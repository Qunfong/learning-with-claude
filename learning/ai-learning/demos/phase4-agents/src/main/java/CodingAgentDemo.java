import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fase 4 — mini "Claude-Code-stijl" coding-agent.
 *
 * De agent krijgt drie tools om een falende test zelf te repareren:
 *   - read_file(path)          -> leest een bestand UIT de sandbox-workspace
 *   - write_file(path,content) -> schrijft een bestand, ALLEEN binnen de sandbox
 *                                  (destructief -- gaat via een confirm-hook)
 *   - run_tests()               -> compileert workspace/*.java en draait CalculatorTest
 *
 * `workspace/Calculator.java` bevat een echte bug (subtract() doet a+b i.p.v.
 * a-b). De agent moet 'm zelf vinden (read_file), zelf fixen (write_file) en
 * zelf verifiëren (run_tests) -- en dat net zo lang herhalen tot de tests
 * slagen of een guardrail ingrijpt. Dit hergebruikt exact dezelfde
 * {@link AgentLoop}/{@link Tool}/{@link Guardrails} als {@code AgentLoopDemo}
 * -- het gedragsverschil zit in de tool-set en het system-prompt, niet in de
 * engine.
 *
 * Sandboxing: elk pad wordt genormaliseerd en gecontroleerd dat het binnen
 * `workspace/` blijft VOORDAT er iets mee gebeurt -- een agent die bestanden
 * mag schrijven is precies waar path-traversal ("../../ergens anders") een
 * reëel risico is, geen theoretisch geval.
 *
 * Draai met: mvn -q compile exec:java -Dexec.mainClass=CodingAgentDemo
 */
public class CodingAgentDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Path WORKSPACE_DIR = Path.of("workspace").toAbsolutePath().normalize();
    // simuleert precies één transiënte fout in run_tests (los van de echte testuitslag) zodat
    // Retry.withBackoff zichtbaar geoefend wordt in een deterministische run, i.p.v. te wachten
    // op een toevallige echte hapering
    static final AtomicBoolean TRANSIENT_ALREADY_SIMULATED = new AtomicBoolean(false);

    record ProcessResult(int exitCode, String output) {}

    // ---- sandbox: elk pad MOET binnen workspace/ blijven -------------------
    static Path resolveSafe(String relPath) {
        Path resolved = WORKSPACE_DIR.resolve(relPath).normalize();
        if (!resolved.startsWith(WORKSPACE_DIR)) {
            throw new IllegalArgumentException(
                    "geweigerd: pad '" + relPath + "' valt buiten de workspace-sandbox");
        }
        return resolved;
    }

    static String requireString(JsonNode args, String field) {
        JsonNode node = args.get(field);
        if (node == null || node.asText().isBlank()) {
            throw new IllegalArgumentException("verplicht argument '" + field + "' ontbreekt");
        }
        return node.asText();
    }

    // ---- tool 1: read_file ---------------------------------------------------
    static String readFile(JsonNode args) {
        Path p = resolveSafe(requireString(args, "path"));
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("bestand bestaat niet: " + args.get("path").asText());
        }
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException("kon bestand niet lezen: " + e.getMessage());
        }
    }

    // ---- tool 2: write_file (destructief) ------------------------------------
    static String writeFile(JsonNode args) {
        String relPath = requireString(args, "path");
        String content = requireString(args, "content");
        Path p = resolveSafe(relPath);
        try {
            Files.writeString(p, content);
            ObjectNode out = JSON.createObjectNode();
            out.put("written", true);
            out.put("path", relPath);
            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException("kon bestand niet schrijven: " + e.getMessage());
        }
    }

    // ---- tool 3: run_tests -- ECHTE javac + java als subprocess -------------
    static String runTests(JsonNode args) {
        return Retry.withBackoff(2, 300, () -> runTestsOnce(args));
    }

    private static String runTestsOnce(JsonNode args) {
        if (TRANSIENT_ALREADY_SIMULATED.compareAndSet(false, true)) {
            throw new Retry.TransientFailure("gesimuleerde tijdelijke storing in de test-runner (bv. flaky CI-runner)");
        }
        try {
            Path outDir = WORKSPACE_DIR.resolve("out");
            Files.createDirectories(outDir);

            List<String> sources = new ArrayList<>();
            try (var stream = Files.list(WORKSPACE_DIR)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> sources.add(p.toString()));
            }

            List<String> compileCmd = new ArrayList<>(List.of("javac", "-d", outDir.toString()));
            compileCmd.addAll(sources);
            ProcessResult compile = runProcess(compileCmd);
            if (compile.exitCode() != 0) {
                ObjectNode fail = JSON.createObjectNode();
                fail.put("passed", false);
                fail.put("phase", "compile");
                fail.put("output", compile.output());
                return fail.toString();
            }

            ProcessResult test = runProcess(List.of("java", "-cp", outDir.toString(), "CalculatorTest"));
            ObjectNode result = JSON.createObjectNode();
            result.put("passed", test.exitCode() == 0);
            result.put("phase", "test");
            result.put("output", test.output());
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("kon tests niet draaien: " + e.getMessage());
        }
    }

    static ProcessResult runProcess(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .directory(WORKSPACE_DIR.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("proces liep vast (timeout 30s): " + String.join(" ", cmd));
        }
        return new ProcessResult(p.exitValue(), output);
    }

    static ObjectNode twoStringParams(String f1, String d1, String f2, String d2) {
        ObjectNode params = JSON.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode p1 = props.putObject(f1); p1.put("type", "string"); p1.put("description", d1);
        ObjectNode p2 = props.putObject(f2); p2.put("type", "string"); p2.put("description", d2);
        params.putArray("required").add(f1).add(f2);
        return params;
    }

    public static void main(String[] args) {
        OllamaClient client = new OllamaClient();
        String model = "llama3.2:3b";

        Tool readFileTool = new Tool(
                "read_file",
                "Lees de inhoud van een bestand in de workspace. Pad is relatief (bv. 'Calculator.java').",
                Tool.oneStringParam("path", "Relatief pad binnen de workspace."),
                false,
                CodingAgentDemo::readFile);

        Tool writeFileTool = new Tool(
                "write_file",
                "Overschrijf een bestand in de workspace met nieuwe inhoud. Pad is relatief.",
                twoStringParams("path", "Relatief pad binnen de workspace.",
                        "content", "Volledige nieuwe inhoud van het bestand."),
                true, // destructief -- gaat via confirm-hook
                CodingAgentDemo::writeFile);

        Tool runTestsTool = new Tool(
                "run_tests",
                "Compileer alle .java-bestanden in de workspace en draai CalculatorTest. " +
                        "Geeft terug of de tests slaagden en de volledige output.",
                Tool.emptyParams(),
                false,
                CodingAgentDemo::runTests);

        Guardrails guardrails = new Guardrails(8, 8000, true,
                (toolName, toolArgs) -> {
                    System.out.println("  [confirm-hook] destructieve actie '" + toolName + "(" + toolArgs +
                            ")' -- auto-goedgekeurd voor deze demo (in productie: mens of policy-check hier)");
                    return true;
                });

        AgentLoop loop = new AgentLoop(client, model,
                "Je bent een coding-agent met toegang tot een sandbox-workspace. Gebruik read_file om code te " +
                        "bekijken, write_file om een fix te schrijven (het volledige bestand opnieuw, niet een diff), " +
                        "en run_tests om te controleren of de fix werkt. Blijf itereren tot de tests slagen. " +
                        "Rapporteer aan het einde kort wat je hebt gefixt.",
                List.of(readFileTool, writeFileTool, runTestsTool),
                guardrails);

        System.out.println("=== Coding-agent: lees -> fix -> test -> herhaal ===");
        System.out.println("workspace/Calculator.java bevat een echte bug (subtract() doet a+b i.p.v. a-b).\n");

        String result = loop.run(
                "De test CalculatorTest faalt. Lees eerst Calculator.java, vind de bug, fix 'm met write_file " +
                        "(schrijf het VOLLEDIGE bestand opnieuw met de fix), en draai daarna run_tests om te " +
                        "verifiëren. Herhaal tot de tests slagen.");

        System.out.println("\neindresultaat: " + result);

        System.out.println("\n=== Sandbox-guard: poging tot path-traversal (buiten de workspace lezen) ===");
        try {
            ObjectNode badArgs = JSON.createObjectNode().put("path", "../pom.xml");
            readFile(badArgs);
        } catch (IllegalArgumentException e) {
            System.out.println("geweigerd: " + e.getMessage() +
                    "  <- dit is precies waarom paden altijd genormaliseerd + gecontroleerd moeten worden");
        }
    }
}
