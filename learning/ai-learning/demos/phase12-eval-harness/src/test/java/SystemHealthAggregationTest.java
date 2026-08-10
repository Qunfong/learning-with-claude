import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layer 3 (system-level health): aggregate tokens/latency/cost across all 6
 * golden runs, and cross-check against the specific figures
 * phase8-autonomous/README.md prints in prose (run3's headline numbers,
 * run6's peak-budget-usage claim). See SystemHealthAggregator's javadoc for
 * exactly which figures are checked and why those two runs.
 */
class SystemHealthAggregationTest {

    @Test
    void aggregatesAcrossAllSixGoldenRuns() throws IOException {
        List<RunTrace> traces = loadAllGolden();
        SystemHealthAggregator.AggregateHealth health = SystemHealthAggregator.aggregate(traces);

        assertEquals(6, health.runCount());
        assertTrue(health.totalTokens() > 0, "aggregate token count should be positive");
        assertTrue(health.totalCostUsd() > 0, "aggregate cost should be positive");
        assertTrue(health.totalDurationMs() > 0, "aggregate duration should be positive");
    }

    @Test
    void everyRunsPerStepTokensAgreeWithItsOwnRunSummary() throws IOException {
        for (RunTrace trace : loadAllGolden()) {
            SystemHealthAggregator.RunHealth h = SystemHealthAggregator.healthOf(trace);
            assertTrue(h.tokenSumConsistent(), trace.runId() + ": per-step token sum disagrees with run_summary total");
        }
    }

    @Test
    void aggregateMatchesReadmesPrintedFigures() throws IOException {
        List<String> discrepancies = SystemHealthAggregator.crossCheckAgainstReadme(loadAllGolden());
        assertTrue(discrepancies.isEmpty(), "aggregate disagrees with phase8-autonomous/README.md's printed figures:\n" + String.join("\n", discrepancies));
    }

    private static List<RunTrace> loadAllGolden() throws IOException {
        List<RunTrace> traces = new ArrayList<>();
        for (GoldenDataset.GoldenRun golden : GoldenDataset.RUNS) {
            traces.add(TraceLoader.loadFromClasspath(golden.resourceName()));
        }
        return traces;
    }
}
