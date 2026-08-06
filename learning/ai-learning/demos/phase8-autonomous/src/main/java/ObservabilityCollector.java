import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * "Observability" from spec: token count, latency, files written, test
 * results, gate decisions and total cost PER RUN, written to
 * {@code traces/run-{id}.jsonl} (structured, queryable), plus an
 * end-of-run summary printed to stdout.
 *
 * Cost model: this demo runs against a LOCAL Ollama instance (free), but the
 * safety rail "max token budget per run (alert + stop)" and the checkpoint
 * format's {@code cost_usd} field are both about a real budgeting concern
 * (see spec Open Question 4: "cost modeling"). So this computes a
 * HYPOTHETICAL hosted-equivalent cost using Claude Sonnet's published
 * per-token pricing as a reference rate -- same convention
 * phase5-skills/SkillsDemo already uses ($3/M input tokens) -- purely so the
 * budgeting mechanics are real and demonstrable even though this particular
 * run costs $0.
 */
class ObservabilityCollector {

    // reference hosted pricing (Claude Sonnet), USD per token -- see phase5-skills/SkillsDemo's $3/M convention
    private static final double PRICE_PER_INPUT_TOKEN = 3.0 / 1_000_000;
    private static final double PRICE_PER_OUTPUT_TOKEN = 15.0 / 1_000_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path traceFile;
    private final int tokenBudget;
    private final Instant startedAt = Instant.now();

    private int totalTokensIn = 0;
    private int totalTokensOut = 0;
    private int stepCount = 0;
    private int filesWritten = 0;
    private int testRuns = 0;
    private int testPasses = 0;
    private final List<String> gateLog = new ArrayList<>();

    ObservabilityCollector(String runId, int tokenBudget) {
        this.traceFile = Path.of("traces", "run-" + runId + ".jsonl");
        this.tokenBudget = tokenBudget;
        try {
            Files.createDirectories(traceFile.getParent());
        } catch (IOException e) {
            System.out.println("(could not create traces/ dir: " + e.getMessage() + ")");
        }
    }

    /** Records one LLM call step. Returns true if the cumulative token budget has now been exceeded. */
    boolean recordStep(String stepName, int tokensIn, int tokensOut, long latencyMs) {
        stepCount++;
        totalTokensIn += tokensIn;
        totalTokensOut += tokensOut;
        double stepCost = tokensIn * PRICE_PER_INPUT_TOKEN + tokensOut * PRICE_PER_OUTPUT_TOKEN;

        trace("step", "step", stepName, "tokensIn", tokensIn, "tokensOut", tokensOut,
                "latencyMs", latencyMs, "cumulativeTokens", totalTokensIn + totalTokensOut,
                "stepCostUsd", round(stepCost));

        System.out.printf("  [observability] step=%-16s tokens(in=%d out=%d) latency=%dms cumulative=%d/%d budget%n",
                stepName, tokensIn, tokensOut, latencyMs, totalTokensIn + totalTokensOut, tokenBudget);

        return tokenBudget > 0 && (totalTokensIn + totalTokensOut) > tokenBudget;
    }

    void recordFileWritten(String path, int chars) {
        filesWritten++;
        trace("file_written", "path", path, "chars", chars);
    }

    void recordTest(boolean passed, long durationMs) {
        testRuns++;
        if (passed) {
            testPasses++;
        }
        trace("test_run", "passed", passed, "durationMs", durationMs);
    }

    void recordGate(String gate, boolean approved, String feedback) {
        gateLog.add(gate + "=" + (approved ? "approved" : "rejected"));
        trace("gate_decision", "gate", gate, "approved", approved,
                "feedback", feedback == null ? "" : feedback);
    }

    double totalCostUsd() {
        return totalTokensIn * PRICE_PER_INPUT_TOKEN + totalTokensOut * PRICE_PER_OUTPUT_TOKEN;
    }

    int totalTokens() {
        return totalTokensIn + totalTokensOut;
    }

    void printSummary(String outcome) {
        Duration elapsed = Duration.between(startedAt, Instant.now());
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("RUN SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("outcome            : %s%n", outcome);
        System.out.printf("duration           : %d.%03ds%n", elapsed.toSeconds(), elapsed.toMillisPart());
        System.out.printf("steps (LLM calls)  : %d%n", stepCount);
        System.out.printf("tokens             : in=%d out=%d total=%d%n", totalTokensIn, totalTokensOut, totalTokens());
        System.out.printf("token budget       : %d (%.0f%% used)%n", tokenBudget,
                tokenBudget > 0 ? 100.0 * totalTokens() / tokenBudget : 0.0);
        System.out.printf("cost (hosted-equiv): $%.4f  (reference pricing, this run used local Ollama = $0 actual)%n",
                totalCostUsd());
        System.out.printf("files written      : %d%n", filesWritten);
        System.out.printf("test runs          : %d (passed=%d)%n", testRuns, testPasses);
        System.out.printf("gate decisions     : %s%n", gateLog.isEmpty() ? "none" : gateLog);
        System.out.printf("trace file         : %s%n", traceFile.toAbsolutePath());
        System.out.println("=".repeat(70));

        trace("run_summary", "outcome", outcome, "durationMs", elapsed.toMillis(),
                "steps", stepCount, "tokensIn", totalTokensIn, "tokensOut", totalTokensOut,
                "costUsd", round(totalCostUsd()), "filesWritten", filesWritten,
                "testRuns", testRuns, "testPasses", testPasses);
    }

    private void trace(String event, Object... kv) {
        ObjectNode node = JSON.createObjectNode();
        node.put("event", event);
        node.put("ts", Instant.now().toString());
        for (int i = 0; i + 1 < kv.length; i += 2) {
            putValue(node, String.valueOf(kv[i]), kv[i + 1]);
        }
        try {
            Files.writeString(traceFile, node + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("(could not write trace: " + e.getMessage() + ")");
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

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
