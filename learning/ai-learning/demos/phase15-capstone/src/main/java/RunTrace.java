import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The run's own record of what happened, in the spirit of
 * {@code phase4-agents/trace.jsonl} and
 * {@code phase8-autonomous/ObservabilityCollector} - an append-only event log
 * that {@link CapstoneEval} then reads.
 *
 * <p>The reason this exists at all is the lesson those two phases keep coming
 * back to: <b>never score a run off its own natural-language summary</b>. The
 * eval layer must read events that were recorded as they happened, by the code
 * that did the thing, not a claim written afterwards.
 *
 * <p>Trimmed vs. phase 12's {@code TraceLoader}/{@code RunTrace}: events are
 * held in memory rather than parsed back off a {@code .jsonl} file. The
 * capstone prints the JSONL for a human to read but never reloads it - one
 * process, one run.
 */
public final class RunTrace {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record Event(String kind, Map<String, Object> fields) {
        public Event {
            fields = new LinkedHashMap<>(fields);
        }

        public String text(String key) {
            Object v = fields.get(key);
            return v == null ? null : String.valueOf(v);
        }

        public boolean flag(String key) {
            return Boolean.TRUE.equals(fields.get(key));
        }

        public double number(String key, double fallback) {
            Object v = fields.get(key);
            return v instanceof Number n ? n.doubleValue() : fallback;
        }
    }

    private final String runId;
    private final List<Event> events = new ArrayList<>();

    public RunTrace(String runId) {
        this.runId = runId;
    }

    public String runId() {
        return runId;
    }

    public void record(String kind, Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected key/value pairs, got " + keyValuePairs.length + " args");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            fields.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        events.add(new Event(kind, fields));
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public List<Event> events(String kind) {
        return events.stream().filter(e -> e.kind().equals(kind)).toList();
    }

    /** The JSONL a phase-12-style offline harness would read back off disk. */
    public List<String> toJsonl() {
        List<String> lines = new ArrayList<>();
        for (Event e : events) {
            ObjectNode node = JSON.createObjectNode();
            node.put("run", runId);
            node.put("event", e.kind());
            e.fields().forEach((k, v) -> {
                if (v instanceof Integer || v instanceof Long) {
                    node.put(k, ((Number) v).longValue());
                } else if (v instanceof Number n) {
                    node.put(k, n.doubleValue());
                } else if (v instanceof Boolean b) {
                    node.put(k, b);
                } else {
                    node.put(k, String.valueOf(v));
                }
            });
            lines.add(node.toString());
        }
        return lines;
    }
}
