import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads {@code run-{id}.jsonl} observability-trace files (one JSON object
 * per line, append-only, same format {@code ObservabilityCollector} writes
 * in phase8-autonomous) into {@link RunTrace}. Read-only -- this harness
 * never writes back into any fixture file it loads.
 */
public final class TraceLoader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TraceLoader() {
    }

    public static RunTrace load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return parse(runIdFromFileName(file.getFileName().toString()), lines);
    }

    /** Loads a trace file from the test classpath (src/test/resources/...). */
    public static RunTrace loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream in = TraceLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("resource not found on classpath: " + resourcePath);
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            String fileName = resourcePath.contains("/")
                    ? resourcePath.substring(resourcePath.lastIndexOf('/') + 1)
                    : resourcePath;
            return parse(runIdFromFileName(fileName), lines);
        }
    }

    /** Loads every {@code *.jsonl} file in a directory, sorted by filename (which sorts by embedded epoch millis for phase8's naming convention). */
    public static List<RunTrace> loadDirectory(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jsonl")) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        List<RunTrace> traces = new ArrayList<>();
        for (Path f : files) {
            traces.add(load(f));
        }
        return traces;
    }

    private static RunTrace parse(String runId, List<String> lines) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            events.add(JSON.readTree(line));
        }
        return new RunTrace(runId, events);
    }

    private static String runIdFromFileName(String fileName) {
        String base = fileName.endsWith(".jsonl") ? fileName.substring(0, fileName.length() - ".jsonl".length()) : fileName;
        return base.startsWith("run-") ? base.substring("run-".length()) : base;
    }
}
