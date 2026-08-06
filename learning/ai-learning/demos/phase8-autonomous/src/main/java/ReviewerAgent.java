import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs before Gate 2 -- capability {@code code.review}, per phase7's
 * ReviewerAgent spec ("Calls: Phase 5 skill (java-standards) + LLM review"),
 * reimplemented self-contained here (no import from
 * demos/phase7-multi-agent/).
 *
 * Hybrid design, deliberately: an LLM-only review is not reliable enough to
 * gate anything on (see phase4-agents/README.md's CodingAgentDemo findings --
 * models fabricate success narratives that don't match ground truth). So
 * this combines:
 *   1. Deterministic static checks (regex) for the most common java-standards
 *      violations (R1 empty catch, R8 System.out.println) -- these are 100%
 *      reproducible, no model involved.
 *   2. An LLM pass using the java-standards skill.md content as system prompt
 *      (exactly phase5-skills' pattern: the skill IS the system prompt),
 *      asked to call out any of R1-R10 the code violates.
 * Neither blocks the pipeline outright in this demo (no REVIEW-fail state in
 * the spec's state diagram) -- findings are surfaced to the human at Gate 2
 * instead, exactly matching the safety-rail's intent ("unreviewed changes"
 * risk is mitigated by the review having RUN and being visible, not by an
 * automatic veto). See spec's Open Question 3 (evals) for where this could
 * go next.
 */
class ReviewerAgent implements A2AAgent {

    private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*}");

    private final OllamaClient client;
    private final String model;
    private final String skillContent;

    ReviewerAgent(OllamaClient client, String model, String skillContent) {
        this.client = client;
        this.model = model;
        this.skillContent = skillContent;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("ReviewerAgent", List.of("code.review"));
    }

    @Override
    public TaskResult handle(Task task) throws Exception {
        String code = task.input();
        List<String> staticIssues = staticLintChecks(code);

        String system = skillContent + """


                ---
                Review the Java code in the next message against R1-R10 above.
                List ONLY rules that are VIOLATED, one per line, format: "Rn: short reason".
                If fully compliant, respond with exactly: COMPLIANT
                """;

        long t0 = System.nanoTime();
        OllamaClient.ChatResult r = client.chat(model, system, code);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        List<String> llmIssues = parseIssues(r.text());
        List<String> allIssues = merge(staticIssues, llmIssues);

        String output = "Static checks: " + (staticIssues.isEmpty() ? "none found" : staticIssues)
                + "\nLLM review (java-standards skill): " + r.text().strip();

        return new TaskResult("done", output, allIssues, r.tokensIn(), r.tokensOut(), ms);
    }

    static List<String> staticLintChecks(String code) {
        List<String> issues = new ArrayList<>();
        if (EMPTY_CATCH.matcher(code).find()) {
            issues.add("R1: empty catch block detected (swallowed exception)");
        }
        if (code.contains("System.out.println") || code.contains("System.err.println")) {
            issues.add("R8: System.out/err.println found (use a logger)");
        }
        if (code.contains("catch (Exception e)") || code.contains("catch (Exception ex)")) {
            issues.add("R2: catches general Exception instead of a specific type");
        }
        return issues;
    }

    private static List<String> parseIssues(String llmText) {
        List<String> issues = new ArrayList<>();
        if (llmText == null || llmText.isBlank() || llmText.strip().equalsIgnoreCase("COMPLIANT")) {
            return issues;
        }
        for (String line : llmText.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.matches("^R\\d+.*")) {
                issues.add(trimmed);
            }
        }
        return issues;
    }

    private static List<String> merge(List<String> a, List<String> b) {
        return new ArrayList<>(new LinkedHashSet<>(concat(a, b)));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
