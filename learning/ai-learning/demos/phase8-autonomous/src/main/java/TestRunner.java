import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TESTING state: runs {@code mvn test} against the fixture module and
 * captures the output, exactly as the spec's pipeline component list
 * requires ("TestRunner (runs: mvn test, captures output)").
 *
 * Chaos-injection (--chaos-fail=N CLI flag, default 0): forces the first N
 * results to report FAILED regardless of the real Maven exit code, so the
 * "3 consecutive test failures -> ESCALATE" path can be demonstrated on
 * demand without depending on the LLM's code-gen quality being bad on a
 * given run. The real `mvn test` still actually runs every time (so
 * duration/output are genuine) -- only the pass/fail VERDICT is overridden,
 * and the override is logged plainly in the captured output so it's never
 * mistaken for a real result. Same "simulate exactly one deterministic
 * failure to exercise a code path" pattern as phase4-agents/CodingAgentDemo's
 * TRANSIENT_ALREADY_SIMULATED.
 */
class TestRunner {

    private final Path fixturePomDir;
    private final AtomicInteger chaosFailuresRemaining;

    TestRunner(Path fixturePomDir, int chaosFailures) {
        this.fixturePomDir = fixturePomDir;
        this.chaosFailuresRemaining = new AtomicInteger(Math.max(0, chaosFailures));
    }

    record Result(boolean passed, long durationMs, String output) {
    }

    Result run() {
        long t0 = System.nanoTime();
        ProcessResult pr;
        try {
            pr = runProcess(List.of("cmd.exe", "/c", "mvn", "-q", "-B", "test"), fixturePomDir);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            return new Result(false, ms, "FAILED TO RUN mvn test: " + e.getMessage());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        boolean realPassed = pr.exitCode() == 0;
        int remaining = chaosFailuresRemaining.getAndUpdate(v -> v > 0 ? v - 1 : v);
        boolean chaosForced = remaining > 0;
        boolean passed = realPassed && !chaosForced;

        String output = pr.output();
        if (chaosForced) {
            output += "\n[chaos-fail] verdict forced to FAIL for demo purposes "
                    + "(real `mvn test` exit code was " + pr.exitCode() + "; "
                    + (remaining - 1) + " forced failure(s) remaining after this one)";
        }
        return new Result(passed, ms, truncate(output, 4000));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    record ProcessResult(int exitCode, String output) {
    }

    private static ProcessResult runProcess(List<String> cmd, Path dir) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("process timed out (120s): " + String.join(" ", cmd));
        }
        return new ProcessResult(p.exitValue(), output);
    }
}
