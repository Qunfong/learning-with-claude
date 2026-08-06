import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * "State Persistence (Checkpointing)" from spec: append-only JSONL writer to
 * {@code run-{id}.jsonl} (module root, matching the spec's literal example
 * filenames). Every event gets an ISO-8601 timestamp added automatically, on
 * top of whatever fields the caller supplies -- the spec's own example only
 * shows a timestamp on {@code run_start}, but having it on every line is
 * strictly more useful for replay/audit and costs nothing.
 *
 * Deliberately dumb: a plain append per event, no buffering, no batching --
 * if the process crashes mid-run, everything written so far is already on
 * disk and re-readable. That's the whole point of checkpointing (see spec's
 * "why": resume from the last gate instead of losing all progress).
 */
class CheckpointStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;

    CheckpointStore(String runId) {
        this.file = Path.of("run-" + runId + ".jsonl");
    }

    Path path() {
        return file;
    }

    /** kv must be an even number of (String key, Object value) pairs. */
    void write(String event, Object... kv) {
        ObjectNode node = JSON.createObjectNode();
        node.put("event", event);
        node.put("ts", Instant.now().toString());
        for (int i = 0; i + 1 < kv.length; i += 2) {
            putValue(node, String.valueOf(kv[i]), kv[i + 1]);
        }
        try {
            Files.writeString(file, node + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("(could not write checkpoint: " + e.getMessage() + ")");
        }
    }

    private static void putValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof Integer i) {
            node.put(key, i);
        } else if (value instanceof Long l) {
            node.put(key, l);
        } else if (value instanceof Double d) {
            node.put(key, d);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else {
            node.put(key, String.valueOf(value));
        }
    }
}
