import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only JSONL audit trail — same shape/discipline as
 * {@code phase8-autonomous}'s {@code CheckpointStore} (plain append, no
 * buffering, every line gets an ISO-8601 timestamp automatically), applied
 * here to credential lifecycle events instead of pipeline checkpoints:
 * {@code mint}, {@code delegate}, {@code use}, {@code scope_denied},
 * {@code expired}, {@code credential_leak_blocked}.
 *
 * <p>Deliberately never accepts a raw token value — every call site passes
 * {@link ScopedCredential#tokenFingerprint()}, never {@link ScopedCredential#token()}.
 * An audit log that itself leaks the secret it's supposed to be auditing
 * would defeat the entire point (this is exactly AWS Module 8's
 * "credentials never enter the log/context" principle, applied to logging
 * instead of prompts).
 */
class AuditLog {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;

    AuditLog(Path file) {
        this.file = file;
    }

    AuditLog() {
        this(Path.of("audit.jsonl"));
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
            System.out.println("(could not write audit entry: " + e.getMessage() + ")");
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
