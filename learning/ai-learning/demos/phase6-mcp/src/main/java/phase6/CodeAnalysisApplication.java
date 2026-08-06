package phase6;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fase 6 — de Java-service die de MCP-server ({@link McpServer}) straks
 * wrapt. Bewust GEEN kennis van MCP hier: dit is gewoon een REST-API die
 * je eigen codebase kent, zoals je die ook zonder AI zou bouwen. MCP komt
 * er pas bovenop in een apart proces (zie McpServer + README) -- dat is
 * precies het punt: de service weet niet dat een model 'm ooit aanroept.
 *
 * Draai met: mvn spring-boot:run
 */
@SpringBootApplication
@RestController
public class CodeAnalysisApplication {

    // demos/ is de map twee niveaus boven waar dit process draait
    // (demos/phase6-mcp) -- zo ziet de service ALLE fases, niet alleen zichzelf
    private static final Path DEMOS_DIR = resolveDemosDir();
    private static final Pattern METHOD_SIGNATURE = Pattern.compile(
            "^\\s*(public|private|protected)\\s+(static\\s+)?[\\w<>\\[\\],\\s]+\\s+\\w+\\s*\\([^;]*\\)\\s*\\{?\\s*$");

    public static void main(String[] args) {
        SpringApplication.run(CodeAnalysisApplication.class, args);
    }

    private static Path resolveDemosDir() {
        Path here = Path.of("").toAbsolutePath();
        // werk je vanuit demos/phase6-mcp (normaal geval, `mvn spring-boot:run`
        // daaruit) of vanuit de repo-root -- beide moeten de demos/-map vinden
        Path candidate = here.endsWith("phase6-mcp") ? here.getParent() : here.resolve("demos");
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("kon demos/-map niet vinden vanaf " + here);
        }
        return candidate;
    }

    // ---- GET /files -- alle .java-bestanden onder demos/, relatief pad ----
    @GetMapping("/files")
    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(DEMOS_DIR)) {
            List<String> out = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target" + java.io.File.separator))
                    .forEach(p -> out.add(DEMOS_DIR.relativize(p).toString().replace('\\', '/')));
            out.sort(String::compareTo);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- GET /files/{name} -- inhoud van EEN bestand, gezocht op bestandsnaam
    @GetMapping("/files/{name}")
    public String readFile(@PathVariable String name) {
        Path match = findByName(name);
        try {
            return Files.readString(match);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- POST /analyze -- regels/methodes/TODO's van EEN bestand -----------
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> body) {
        Path match = findByName(body.get("name"));
        return analyzeFile(match);
    }

    // ---- GET /metrics -- geaggregeerd over ALLE .java-bestanden ------------
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        List<String> files = listFiles();
        int totalLines = 0, totalMethods = 0, totalTodos = 0;
        for (String rel : files) {
            Map<String, Object> a = analyzeFile(DEMOS_DIR.resolve(rel));
            totalLines += (int) a.get("lines");
            totalMethods += (int) a.get("methods");
            totalTodos += (int) a.get("todos");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("files", files.size());
        out.put("totalLines", totalLines);
        out.put("totalMethods", totalMethods);
        out.put("totalTodos", totalTodos);
        return out;
    }

    // ---- shared analyse-logica, hergebruikt door /analyze en /metrics ------
    private static Map<String, Object> analyzeFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            long methods = lines.stream().filter(l -> METHOD_SIGNATURE.matcher(l).matches()).count();
            long todos = lines.stream().filter(l -> l.contains("TODO")).count();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("file", DEMOS_DIR.relativize(file).toString().replace('\\', '/'));
            out.put("lines", lines.size());
            out.put("methods", (int) methods);
            out.put("todos", (int) todos);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 404 i.p.v. Spring's generieke 500 -- de MCP-server relayt deze message
    // direct terug naar het model als tool-resultaat, dus 'm moet leesbaar zijn
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // bestandsnaam (bv. "AgentLoop.java") -> volledig pad, overal onder demos/
    private static Path findByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' is verplicht");
        }
        try (Stream<Path> walk = Files.walk(DEMOS_DIR)) {
            return walk.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("bestand niet gevonden: " + name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
