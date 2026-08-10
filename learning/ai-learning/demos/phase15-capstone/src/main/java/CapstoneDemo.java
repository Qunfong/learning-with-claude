import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The capstone: one narrated run that chains six of the curriculum's
 * techniques end to end.
 *
 * <pre>
 *   phase 9  scoped credential minted, then delegated NARROWER
 *   phase 4  agent loop plan step, checked by guardrails
 *   phase 3  tool call - executed only if the credential authorizes it
 *   phase 11 schema-validated handoff, then a confidence gate that can abstain
 *   phase 13 ranked, bounded, decayed memory write and recall
 *   phase 12 deterministic composite score over the run's own trace
 * </pre>
 *
 * Three runs of the same chain, differing only in what the agent hands off:
 * a confident well-formed result (completes), a low-confidence one (abstains),
 * and a malformed one (hard-stops at the schema gate). Everything is
 * deterministic and offline - no model, no network, no files written.
 */
public final class CapstoneDemo {

    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final String TASK_ID = "INC-4471-handover";

    /** All capabilities the human principal holds. The agent will get fewer. */
    private static final Set<String> ROOT_SCOPES =
            new LinkedHashSet<>(List.of("read:incident", "send:email", "delete:incident"));

    public static void main(String[] args) {
        System.out.println("=".repeat(78));
        System.out.println("Phase 15 capstone -- one chain, six phases' techniques");
        System.out.println("Task: draft the on-call handover note for incident INC-4471");
        System.out.println("=".repeat(78));

        Map<String, RunTrace> traces = new LinkedHashMap<>();
        traces.put("A (confident)", runChain("Run A -- confident agent, chain completes",
                SummarizerAgent.Mode.NORMAL));
        traces.put("B (low confidence)", runChain("Run B -- low-confidence agent, pipeline abstains",
                SummarizerAgent.Mode.LOW_CONFIDENCE));
        traces.put("C (malformed handoff)", runChain("Run C -- malformed handoff, schema gate hard-stops",
                SummarizerAgent.Mode.MALFORMED_HANDOFF));

        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println("Comparison (phase 12: same scorer, three runs)");
        System.out.println("=".repeat(78));
        System.out.printf(Locale.ROOT, "%-24s %10s %12s %s%n", "run", "score", "CI gate", "violations");
        traces.forEach((label, trace) -> {
            double score = CapstoneEval.score(trace);
            List<String> violations = CapstoneEval.violations(trace);
            System.out.printf(Locale.ROOT, "%-24s %10.2f %12s %s%n",
                    label, score,
                    score >= CapstoneEval.CI_GATE ? "PASS" : "BLOCK",
                    violations.isEmpty() ? "none" : violations);
        });
        System.out.println();
        System.out.println("Runs B and C score low and have ZERO structural violations -- that pairing is");
        System.out.println("the point. The score answers \"did this run produce a shippable result?\";");
        System.out.println("the violation list answers \"did the machinery behave correctly?\". Abstaining");
        System.out.println("on a shaky summary is correct behaviour AND an unshippable run, and a scorer");
        System.out.println("that cannot say both things at once will eventually wave one of them through.");
    }

    // ------------------------------------------------------------------
    // the chain
    // ------------------------------------------------------------------

    static RunTrace runChain(String title, SummarizerAgent.Mode mode) {
        RunTrace trace = new RunTrace(title);
        System.out.println();
        System.out.println("-".repeat(78));
        System.out.println(title);
        System.out.println("-".repeat(78));

        // --- Step 1/6: scoped credential issuance (phase 9) -------------
        System.out.println("[1/6] scoped credential issuance                      (phase 9 -- agent IAM)");
        CredentialBroker broker = new CredentialBroker();
        ScopedCredential root = broker.mintRoot("EndUser", ROOT_SCOPES, Duration.ofMinutes(15), NOW);
        System.out.printf("      mintRoot   %s  chain=%s  scopes=%s  expires=%s%n",
                root.id(), root.chainString(), root.grantedScopes(), root.expiresAt());

        ScopedCredential orchestrator = broker.delegate(root, "OrchestratorAgent",
                new LinkedHashSet<>(List.of("read:incident", "send:email")), Duration.ofMinutes(10), NOW);
        ScopedCredential agentCred = broker.delegate(orchestrator, "SummarizerAgent",
                Set.of("read:incident"), Duration.ofMinutes(30), NOW);
        System.out.printf("      delegate   %s  chain=%s  scopes=%s%n",
                orchestrator.id(), orchestrator.chainString(), orchestrator.grantedScopes());
        System.out.printf("      delegate   %s  chain=%s  scopes=%s%n",
                agentCred.id(), agentCred.chainString(), agentCred.grantedScopes());
        System.out.printf("      ttl asked 30m, expiry granted %s -- capped at the parent's; a delegate%n",
                agentCred.expiresAt());
        System.out.println("      cannot mint itself more time or more scope than it was handed.");
        System.out.printf("      blast radius: %d of %d capabilities reachable if SummarizerAgent is owned%n",
                agentCred.grantedScopes().size(), ROOT_SCOPES.size());
        System.out.printf("      token never enters the model's context; logged as %s only%n",
                agentCred.tokenFingerprint());
        trace.record("credential_minted", "credentialId", agentCred.id(),
                "principal", agentCred.principal(), "scopes", String.join(",", agentCred.grantedScopes()));

        // --- Steps 2+3: agent loop with guardrails, credential-gated tools
        SummarizerAgent agent = new SummarizerAgent(mode);
        Guardrails guardrails = new Guardrails(4);
        Map<String, Tool> tools = tools();
        List<String> observations = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        System.out.println();
        System.out.println("[2/6] agent loop: plan -> guardrails                  (phase 4 -- agent loop)");
        System.out.println("[3/6] act: credential-gated tool call                 (phase 3 + 9 -- tools)");
        System.out.printf("      tools the model can see: %s%n", tools.keySet());
        System.out.println("      note: Tool.schema() omits requiredScope -- the model is never told");
        System.out.println("      which capability a tool needs, and cannot ask for a credential.");

        for (int iteration = 1; iteration <= guardrails.maxIterations(); iteration++) {
            SummarizerAgent.PlanStep step = agent.plan(iteration);
            System.out.printf("      iter %d  plan: %s%n", iteration, step.thought());
            if (step.terminal()) {
                System.out.println("      iter " + iteration + "  no further tool needed -- handing off");
                break;
            }

            String argsJson = step.args().toString();
            String blocked = guardrails.checkStep(iteration, step.tool(), argsJson);
            if (blocked != null) {
                System.out.printf("      iter %d  GUARDRAIL STOP: %s%n", iteration, blocked);
                issues.add("guardrail stopped the loop: " + blocked);
                break;
            }

            Tool tool = tools.get(step.tool());
            SecureToolInvoker.Invocation invocation =
                    SecureToolInvoker.invoke(agentCred, tool, step.args(), NOW);
            trace.record("tool_call", "tool", tool.name(), "requiredScope", tool.requiredScope(),
                    "authorized", invocation.authorized(), "executed", invocation.executed());

            if (invocation.authorized()) {
                System.out.printf("      iter %d  act: %s%s -> AUTHORIZED (needs %s)%n",
                        iteration, tool.name(), argsJson, tool.requiredScope());
                System.out.printf("      iter %d  observe: %s%n", iteration, invocation.result());
                observations.add(invocation.result());
            } else {
                System.out.printf("      iter %d  act: %s%s -> DENIED (%s)%n",
                        iteration, tool.name(), argsJson, invocation.denialReason());
                System.out.println("               the executor never ran -- denial happens before it,");
                System.out.println("               not inside it, so there is nothing to undo.");
                issues.add("could not email the handover: " + invocation.denialReason());
            }
        }

        // --- Step 4/6: schema gate, then confidence gate (phase 11) ------
        System.out.println();
        System.out.println("[4/6] handoff: schema gate, then confidence gate      (phase 11 -- resilience)");
        String rawHandoff = agent.buildHandoffJson(TASK_ID, observations, issues);
        System.out.printf("      raw agent output: %s%n", truncate(rawHandoff));

        TaskResult result;
        try {
            result = HandoffValidator.parseAndValidate(rawHandoff);
        } catch (SchemaValidationException e) {
            System.out.println("      SCHEMA GATE: REJECTED -- " + e.violations());
            System.out.println("      hard stop. The payload is not coerced into a default TaskResult;");
            System.out.println("      a malformed handoff is a bug to fix, not a value to guess at.");
            System.out.println("      step 5 (memory) never runs -- there is no validated result to store.");
            trace.record("handoff", "schemaValid", false, "confidence", 0.0, "forwarded", false);
            trace.record("run_summary", "outcome", "FAILED_VALIDATION");
            report(trace);
            return trace;
        }
        System.out.printf("      SCHEMA GATE: OK -- taskId/state/artifacts/issues/message/confidence "
                + "present and well-typed, state '%s' in %s%n", result.state(), TaskResult.VALID_STATES);

        boolean forwarded = passesConfidenceGate(result);
        trace.record("handoff", "schemaValid", true, "confidence", result.confidence(),
                "forwarded", forwarded);

        if (!forwarded) {
            System.out.printf(Locale.ROOT, "      CONFIDENCE GATE: %.2f < %.2f -- ABSTAINING%n",
                    result.confidence(), CapstoneEval.CONFIDENCE_THRESHOLD);
            System.out.println("      the result was schema-VALID; those are two different questions.");
            System.out.println("      nothing is written to memory and nothing is forwarded -- a shaky");
            System.out.println("      handover note that reads as authoritative is worse than none.");
            result.issues().forEach(i -> System.out.println("        unresolved: " + i));
            System.out.println("      step 5 (memory) never runs -- an abstention that still writes to");
            System.out.println("      memory is not an abstention (CapstoneEval re-checks this off the trace).");
            trace.record("run_summary", "outcome", "ABSTAINED");
            report(trace);
            return trace;
        }
        System.out.printf(Locale.ROOT, "      CONFIDENCE GATE: %.2f >= %.2f -- forwarding%n",
                result.confidence(), CapstoneEval.CONFIDENCE_THRESHOLD);

        // --- Step 5/6: ranked memory write + recall (phase 13) -----------
        System.out.println();
        System.out.println("[5/6] memory: ranked, bounded, decayed                (phase 13 -- memory v2)");
        RankedMemory memory = seededMemory();
        System.out.printf("      %d prior-run facts already stored%n", memory.size());
        memory.remember("INC-4471 root cause: a connection-pool change; checkout latency now recovered",
                "this-run");
        memory.remember("INC-4471 handover owner: the payments squad, next check 14:00 UTC",
                "this-run");
        trace.record("memory_write", "facts", 2);
        System.out.println("      wrote 2 facts from this run");

        String query = "what caused the checkout latency incident INC-4471 and who owns the handover";
        List<RankedMemory.ScoredFact> recalled = memory.recall(query, 5);
        long fromThisRun = recalled.stream().filter(f -> f.source().equals("this-run")).count();
        System.out.printf("      recall(\"%s\", topK=5)%n", query);
        System.out.printf("      asked for 5, hard bound is %d -- got %d%n",
                RankedMemory.MAX_RESULTS, recalled.size());
        for (RankedMemory.ScoredFact f : recalled) {
            System.out.printf(Locale.ROOT, "        %.4f = sim %.4f x decay %.4f  [%s] %s%n",
                    f.rankScore(), f.similarity(), f.decay(), f.source(), truncate(f.text()));
        }
        trace.record("memory_recall", "requested", 5, "returned", recalled.size(),
                "fromThisRun", (int) fromThisRun);
        System.out.println("      honest note: this ranking is lexical only -- a 64-dimension hashed");
        System.out.println("      bag-of-words with no semantics, so an unrelated prior incident still");
        System.out.println("      outranks one of this run's own facts purely on shared words like");
        System.out.println("      \"root cause\". Phase 13's BM25 + vector + RRF + rerank stack exists");
        System.out.println("      precisely because plain cosine over a toy embedding is not enough.");

        // --- Step 6/6: deterministic composite score (phase 12) ----------
        trace.record("run_summary", "outcome", "COMPLETED");
        report(trace);
        return trace;
    }

    /**
     * Phase 11's abstention rule, extracted so a test can pin it directly the
     * way {@code HandoffValidatorAbstentionTest} pins
     * {@code ResilientPipeline.passesConfidenceGate}.
     */
    public static boolean passesConfidenceGate(TaskResult result) {
        return result.confidence() >= CapstoneEval.CONFIDENCE_THRESHOLD;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void report(RunTrace trace) {
        System.out.println();
        System.out.println("[6/6] evaluation over this run's own trace            (phase 12 -- eval harness)");
        System.out.println("      the trace a phase-12 harness would read back (never the agent's own");
        System.out.println("      summary of what it did):");
        trace.toJsonl().forEach(line -> System.out.println("        " + line));
        List<String> violations = CapstoneEval.violations(trace);
        double score = CapstoneEval.score(trace);
        System.out.printf("      structural violations: %s%n", violations.isEmpty() ? "none" : violations);
        System.out.printf(Locale.ROOT, "      composite score: %.2f / 100   CI gate at %.0f -> %s%n",
                score, CapstoneEval.CI_GATE, score >= CapstoneEval.CI_GATE ? "PASS" : "BLOCK");
    }

    private static Map<String, Tool> tools() {
        Map<String, Tool> tools = new LinkedHashMap<>();
        tools.put("read_incident", new Tool(
                "read_incident",
                "Fetch an incident record by id.",
                Tool.oneStringParam("id", "incident id, e.g. INC-4471"),
                "read:incident",
                CapstoneDemo::readIncident));
        tools.put("send_email", new Tool(
                "send_email",
                "Email a note to a recipient.",
                Tool.oneStringParam("to", "recipient address"),
                "send:email",
                args -> "sent to " + args.path("to").asText()));
        tools.put("delete_incident", new Tool(
                "delete_incident",
                "Permanently delete an incident record.",
                Tool.oneStringParam("id", "incident id"),
                "delete:incident",
                args -> "deleted " + args.path("id").asText()));
        return tools;
    }

    private static String readIncident(com.fasterxml.jackson.databind.JsonNode args) {
        if (!"INC-4471".equals(args.path("id").asText())) {
            return "FOUND_NONE";
        }
        return "INC-4471 checkout latency p99 4.2s from 08:12 to 08:40 UTC, "
                + "cause: connection-pool size lowered to 4 in deploy 2026.08.10-1, "
                + "mitigation: pool reverted to 32, p99 now 310ms";
    }

    private static RankedMemory seededMemory() {
        RankedMemory memory = new RankedMemory();
        memory.remember("INC-4102 root cause: expired TLS certificate on the payments gateway; "
                + "renewed and expiry alerting added", "prior-run");
        memory.remember("Runbook: every on-call handover note must name the current owner "
                + "and the next check time", "prior-run");
        memory.remember("INC-3980 root cause: a bad feature-flag rollout in the search service; "
                + "rolled back within 12 minutes", "prior-run");
        memory.remember("Deployment freeze applies from Friday 16:00 until Monday 09:00 UTC",
                "prior-run");
        return memory;
    }

    private static String truncate(String text) {
        return text.length() <= 96 ? text : text.substring(0, 93) + "...";
    }

    private CapstoneDemo() {
    }
}
