import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the aggregate score is usable as a CI-style gate, not just a
 * report generator: feed a deliberately "worse" synthetic trace (3 retries,
 * escalated outcome, lower per-step confidence markers) alongside a
 * "better" one (first-try success, high confidence, low token spend) and
 * confirm the harness's score ranks them correctly and by a meaningful
 * margin -- not a coin-flip-sized gap that would be noise in a real gate.
 */
class RegressionDetectionTest {

    @Test
    void betterRunScoresHigherThanWorseRun() throws IOException {
        RunTrace better = TraceLoader.loadFromClasspath("synthetic/better-run.jsonl");
        RunTrace worse = TraceLoader.loadFromClasspath("synthetic/worse-run.jsonl");

        double betterScore = EvalHarness.score(better);
        double worseScore = EvalHarness.score(worse);

        assertTrue(betterScore > worseScore,
                "better-run.jsonl (score=" + betterScore + ") must outscore worse-run.jsonl (score=" + worseScore + ")");

        double margin = betterScore - worseScore;
        assertTrue(margin >= 20.0,
                "expected a meaningful CI-gate-worthy margin (>=20 points), got " + margin
                        + " (better=" + betterScore + ", worse=" + worseScore + ")");
    }

    @Test
    void worseRunReflectsItsOwnRetriesAndLowerConfidence() throws IOException {
        RunTrace worse = TraceLoader.loadFromClasspath("synthetic/worse-run.jsonl");

        assertTrue(worse.codingSteps().size() == 3, "worse-run fixture should record 3 CODING attempts (2 retries)");
        assertTrue(worse.averageConfidenceOrNeutral() < 0.5, "worse-run fixture should carry low average confidence markers");
        assertTrue(worse.recordedOutcomeIsEscalate(), "worse-run fixture should have escalated");
    }
}
