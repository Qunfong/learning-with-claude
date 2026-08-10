import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AWS three-layer framework, layer 3: system-level health. Aggregates
 * tokens/latency/cost across every ingested run, and cross-checks the
 * aggregate against phase8's own README-printed numbers (from its
 * ObservabilityCollector's stdout summary) as independent ground truth --
 * NOT numbers this harness invented, numbers a human already read off a
 * real terminal and wrote down.
 */
public final class SystemHealthAggregator {

    public record RunHealth(String runId, long tokensIn, long tokensOut, double costUsd, long durationMs, boolean tokenSumConsistent) {
        public long totalTokens() {
            return tokensIn + tokensOut;
        }
    }

    public record AggregateHealth(int runCount, long totalTokensIn, long totalTokensOut, double totalCostUsd,
                                   long totalDurationMs, RunHealth peakTokenRun, List<RunHealth> perRun) {
        public long totalTokens() {
            return totalTokensIn + totalTokensOut;
        }
    }

    private SystemHealthAggregator() {
    }

    public static RunHealth healthOf(RunTrace trace) {
        boolean consistent = trace.recomputedTotalTokens() == (trace.totalTokensIn() + trace.totalTokensOut());
        return new RunHealth(trace.runId(), trace.totalTokensIn(), trace.totalTokensOut(), trace.totalCostUsd(), trace.durationMs(), consistent);
    }

    public static AggregateHealth aggregate(List<RunTrace> traces) {
        List<RunHealth> perRun = new ArrayList<>();
        long totalIn = 0, totalOut = 0, totalDuration = 0;
        double totalCost = 0;
        for (RunTrace t : traces) {
            RunHealth h = healthOf(t);
            perRun.add(h);
            totalIn += h.tokensIn();
            totalOut += h.tokensOut();
            totalCost += h.costUsd();
            totalDuration += h.durationMs();
        }
        RunHealth peak = perRun.stream().max(Comparator.comparingLong(RunHealth::totalTokens)).orElse(null);
        return new AggregateHealth(traces.size(), totalIn, totalOut, totalCost, totalDuration, peak, perRun);
    }

    /**
     * Cross-checks specific figures phase8-autonomous/README.md prints in prose against the actual trace
     * data. Returns a list of human-readable discrepancies -- empty means every checked figure matches.
     *
     * Figures checked (transcribed from the README's "What actually happened" section):
     *  - Run 3 ("full success"): "3 LLM steps, 3595 total tokens, $0.0200 hosted-equivalent cost, 44.4s wall clock."
     *  - Run 6: "peak was 29% of [the 40000 token] budget" -- the highest-token run of the six.
     */
    public static List<String> crossCheckAgainstReadme(List<RunTrace> traces) {
        List<String> discrepancies = new ArrayList<>();
        final int tokenBudget = 40000;

        traces.stream().filter(t -> t.runId().contains("1785276196928")).findFirst().ifPresentOrElse(run3 -> {
            long tokens = run3.totalTokensIn() + run3.totalTokensOut();
            if (tokens != 3595) {
                discrepancies.add("run3: README claims 3595 total tokens, trace has " + tokens);
            }
            if (Math.abs(run3.totalCostUsd() - 0.02) > 0.0001) {
                discrepancies.add("run3: README claims $0.0200 cost, trace has $" + run3.totalCostUsd());
            }
            if (Math.abs(run3.durationMs() - 44448) > 50) {
                discrepancies.add("run3: README claims 44.4s (44448ms) duration, trace has " + run3.durationMs() + "ms");
            }
        }, () -> discrepancies.add("run3 (1785276196928) not present in ingested traces -- cannot cross-check README's headline success-run figures"));

        traces.stream().filter(t -> t.runId().contains("1785276655035")).findFirst().ifPresentOrElse(run6 -> {
            long tokens = run6.totalTokensIn() + run6.totalTokensOut();
            double fraction = (double) tokens / tokenBudget;
            // README says "peak was 29% of it, run 6" -- allow +/- 1 percentage point for rounding.
            if (Math.abs(fraction - 0.29) > 0.01) {
                discrepancies.add(String.format("run6: README claims peak token usage was 29%% of the %d budget, trace computes %.1f%%",
                        tokenBudget, fraction * 100));
            }
        }, () -> discrepancies.add("run6 (1785276655035) not present in ingested traces -- cannot cross-check README's peak-budget-usage claim"));

        // Every run's own recomputed step-token sum should agree with its run_summary total (internal consistency, not just README cross-check).
        for (RunTrace t : traces) {
            if (t.recomputedTotalTokens() != (t.totalTokensIn() + t.totalTokensOut())) {
                discrepancies.add(t.runId() + ": sum of per-step tokens (" + t.recomputedTotalTokens()
                        + ") disagrees with run_summary total (" + (t.totalTokensIn() + t.totalTokensOut()) + ")");
            }
        }

        return discrepancies;
    }
}
