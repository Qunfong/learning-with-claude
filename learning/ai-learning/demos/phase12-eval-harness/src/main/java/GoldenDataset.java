import java.util.List;

/**
 * Hand-labeled golden set: the 6 real, live phase8-autonomous runs
 * documented in {@code phase8-autonomous/README.md}'s "What actually
 * happened" section, in the order they occurred (their filenames'
 * embedded epoch-millis timestamps sort chronologically, which matches
 * the README's numbered list 1-6).
 *
 * These are NOT synthetic -- every trace file this references is a copy of
 * a real, previously-committed `traces/run-DEMO-001-*.jsonl` from a live
 * Ollama run against `llama3.2:3b` (planning/review) and `qwen2.5-coder:7b`
 * (coding). The expected outcome and reason for each is transcribed
 * directly from that README, not derived from the harness itself -- this
 * is the whole point of a golden set: an independently-known-correct label
 * to regress the harness's own classification logic against.
 */
public final class GoldenDataset {

    public enum Outcome { SUCCESS, ESCALATE }

    public record GoldenRun(String resourceName, String runIdSuffix, Outcome expected, String reason) {
    }

    public static final List<GoldenRun> RUNS = List.of(
            new GoldenRun("phase8-traces/run-DEMO-001-1785275876344.jsonl", "1785275876344", Outcome.ESCALATE,
                    "Run 1 -- organic ESCALATE: qwen2.5-coder:7b added retry logic using org.slf4j.Logger, which "
                            + "fixture/pom.xml didn't yet depend on -- 3 consecutive real compile failures."),
            new GoldenRun("phase8-traces/run-DEMO-001-1785276048587.jsonl", "1785276048587", Outcome.ESCALATE,
                    "Run 2 -- organic ESCALATE: the model renamed the public complete(HttpCall) method to "
                            + "executeHttpCall(...), breaking the test's contract -- 3 consecutive real compile failures."),
            new GoldenRun("phase8-traces/run-DEMO-001-1785276196928.jsonl", "1785276196928", Outcome.SUCCESS,
                    "Run 3 -- full success, --auto: PLANNING -> GATE1(auto) -> CODING -> TESTING(passed, real mvn "
                            + "test 4.6s) -> GATE2(auto) -> PR OPEN (simulated, non-git-repo)."),
            new GoldenRun("phase8-traces/run-DEMO-001-1785276268863.jsonl", "1785276268863", Outcome.SUCCESS,
                    "Run 4 -- interactive Gate1 rejection -> re-plan -> approve -> full success (real stdin, "
                            + "feedback loop verified in the second plan's own text)."),
            new GoldenRun("phase8-traces/run-DEMO-001-1785276341273.jsonl", "1785276341273", Outcome.ESCALATE,
                    "Run 5 -- --chaos-fail=3: real mvn test passed all 3 attempts (exit code 0 every time) but the "
                            + "verdict was forced to FAIL, deterministically driving RETRY x2 -> ESCALATE."),
            new GoldenRun("phase8-traces/run-DEMO-001-1785276655035.jsonl", "1785276655035", Outcome.ESCALATE,
                    "Run 6 -- organic ESCALATE, a third distinct real bug: off-by-one in the retry loop "
                            + "(<=maxRetries -> 4 attempts, not 3) -- 3 consecutive real test failures.")
    );
}
