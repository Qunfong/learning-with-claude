import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The deck's 16 diagrams, one method each, hand-placed on top of
 * {@link DiagramRenderer}.
 *
 * <p>Auto-layout was deliberately not used. Every diagram here has 5-9 nodes and
 * exactly one thing it is trying to show; a router that does not know which edge
 * is the point will happily bend that one. So coordinates are chosen by hand:
 * the flow reads left-to-right or top-to-bottom, loop-backs route <i>around</i>
 * the boxes they skip instead of through them, and every branch edge carries its
 * condition as a label.
 *
 * <p>Colour is assigned by node <b>role</b>, never by phase: cream = an ordinary
 * step, amber diamond = a branch, red = a terminal the run does not continue
 * past, green = an entry or a success, teal = a participant in a sequence
 * diagram. Read one diagram and you can read all sixteen.
 *
 * <p>{@link DiagramRenderer#finish()} refuses to emit a diagram whose shapes
 * overlap, whose labels land on a shape, or that spills outside its viewBox, so
 * a layout mistake here breaks the build rather than shipping a mangled picture.
 */
public final class PhaseDiagrams {

    private static final DiagramRenderer.Kind STEP = DiagramRenderer.Kind.PROCESS;
    private static final DiagramRenderer.Kind ENTRY = DiagramRenderer.Kind.START;
    private static final DiagramRenderer.Kind HALT = DiagramRenderer.Kind.STOP;

    private PhaseDiagrams() {
    }

    /** Writes {@code phase0.svg} .. {@code phase14.svg} plus {@code combined-architecture.svg}. */
    public static List<Path> writeAll(Path dir) {
        try {
            Files.createDirectories(dir);
            List<Path> written = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                Path file = dir.resolve("phase" + i + ".svg");
                Files.writeString(file, render(i));
                written.add(file);
            }
            Path combined = dir.resolve("combined-architecture.svg");
            Files.writeString(combined, combined());
            written.add(combined);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write diagrams to " + dir.toAbsolutePath(), e);
        }
    }

    static String render(int phase) {
        return switch (phase) {
            case 0 -> phase0();
            case 1 -> phase1();
            case 2 -> phase2();
            case 3 -> phase3();
            case 4 -> phase4();
            case 5 -> phase5();
            case 6 -> phase6();
            case 7 -> phase7();
            case 8 -> phase8();
            case 9 -> phase9();
            case 10 -> phase10();
            case 11 -> phase11();
            case 12 -> phase12();
            case 13 -> phase13();
            case 14 -> phase14();
            default -> throw new IllegalArgumentException("no diagram defined for phase " + phase);
        };
    }

    private static DiagramRenderer.Pt pt(double x, double y) {
        return new DiagramRenderer.Pt(x, y);
    }

    // ---- phase 0: the autoregressive loop ------------------------------

    private static String phase0() {
        var d = new DiagramRenderer(880, 260,
                "Text is tokenized to ids, the model turns ids into logits over the whole vocabulary, "
                        + "sampling collapses that distribution to one token, and the token is appended "
                        + "and fed back in.");
        var text = d.box(60, 54, ENTRY, "text");
        var bpe = d.box(190, 54, STEP, "BPE tokenizer");
        var ids = d.box(340, 54, STEP, "token ids");
        var model = d.box(510, 54, STEP, "model forward pass");
        var logits = d.box(730, 54, STEP, "logits over vocabulary");
        var sampling = d.box(730, 200, STEP, "sampling:", "temperature, top-p, top-k");
        var next = d.box(400, 200, STEP, "next token");

        d.arrow(text.right(), bpe.left());
        d.arrow(bpe.right(), ids.left());
        d.arrow(ids.right(), model.left());
        d.arrow(model.right(), logits.left());
        d.arrow(logits.bottom(), sampling.top());
        d.arrow(sampling.left(), next.right());
        // the loop-back: routes below the top row, never across it
        d.elbow(pt(370, 120), "next token appended",
                next.top(), pt(400, 120), pt(340, 120), ids.bottom());
        return d.finish();
    }

    // ---- phase 1: one interface, two backends --------------------------

    private static String phase1() {
        var d = new DiagramRenderer(880, 300,
                "One ModelClient interface fans out to a local quantized backend and a hosted API; "
                        + "both converge on the same four-axis tradeoff.");
        var svc = d.box(85, 150, ENTRY, "Java service");
        var iface = d.box(290, 150, STEP, "ModelClient interface");
        var local = d.box(520, 70, STEP, "local: Ollama, llama.cpp");
        var hosted = d.box(520, 240, STEP, "hosted: Claude API");
        var quant = d.box(740, 70, STEP, "quantized weights:", "GGUF, AWQ, GPTQ");
        var tradeoff = d.box(740, 240, STEP, "latency, cost,", "privacy, capability");

        d.arrow(svc.right(), iface.left());
        d.arrow(iface.right(), local.at(-1, 0.4));
        d.pill(399, 110, DiagramRenderer.INK_DIM, "in-process");
        d.arrow(iface.right(), hosted.at(-1, -0.4));
        d.pill(420, 192, DiagramRenderer.INK_DIM, "network hop");
        d.arrow(local.right(), quant.left());
        d.arrow(quant.bottom(), tradeoff.top(), "smaller, faster, lossier");
        d.arrow(hosted.right(), tradeoff.left());
        return d.finish();
    }

    // ---- phase 2: retrieval assembles the context ----------------------

    private static String phase2() {
        var d = new DiagramRenderer(890, 300,
                "A corpus is chunked into a vector store; a question is embedded, matched by cosine "
                        + "similarity, and the top-k chunks are assembled into the prompt.");
        var corpus = d.box(90, 70, ENTRY, "code corpus");
        var chunk = d.box(240, 70, STEP, "chunk");
        var store = d.box(390, 70, STEP, "vector store");
        var question = d.box(90, 230, ENTRY, "question");
        var embed = d.box(240, 230, STEP, "embed query");
        var search = d.box(430, 230, STEP, "cosine search");
        var topk = d.box(600, 230, STEP, "top-k chunks");
        var prompt = d.box(770, 230, STEP, "prompt: system,", "context, question");
        var llm = d.box(770, 80, STEP, "LLM");

        d.arrow(corpus.right(), chunk.left());
        d.arrow(chunk.right(), store.left());
        d.arrow(question.right(), embed.left());
        d.arrow(embed.right(), search.left());
        d.arrow(store.bottom(), search.at(-0.5, -1));
        d.pill(394, 150, DiagramRenderer.INK_DIM, "chunk vectors");
        d.arrow(search.right(), topk.left());
        d.arrow(topk.right(), prompt.left());
        d.arrow(prompt.top(), llm.bottom(), "assembled context");
        return d.finish();
    }

    // ---- phase 3: a tool call is a round trip through your code --------

    private static String phase3() {
        var d = new DiagramRenderer(680, 500,
                "The model never calls the tool: it returns a name plus JSON args, the agent executes "
                        + "the call, and the result goes back into the message list.");
        var ll = d.lifelines(40, 60, 184.4, 470, "Agent", "Model", "Tool");
        var agent = ll[0];
        var model = ll[1];
        var tool = ll[2];

        d.message(agent, model, 120, false, "prompt + tool schemas");
        d.message(model, agent, 182, true, "tool_call: name +", "args JSON");
        d.message(agent, tool, 244, false, "execute(args)");
        d.message(tool, agent, 306, true, "result");
        d.message(agent, model, 368, false, "tool result appended", "to messages");
        d.message(model, agent, 430, true, "final answer");
        return d.finish();
    }

    // ---- phase 4: the loop, and the rails around it --------------------

    private static String phase4() {
        var d = new DiagramRenderer(560, 500,
                "Plan, act, observe, repeat - with two exits the loop does not control: the goal test "
                        + "and a guardrail trip on iteration count or a repeated call.");
        var plan = d.box(250, 62, ENTRY, "plan");
        var act = d.box(250, 152, STEP, "act: tool call");
        var observe = d.box(250, 242, STEP, "observe: tool result");
        var goal = d.diamond(250, 345, "goal reached?");
        var done = d.box(470, 345, ENTRY, "done");
        var stop = d.box(250, 452, HALT, "max iterations or", "loop detected");

        d.arrow(plan.bottom(), act.top());
        d.arrow(act.bottom(), observe.top());
        d.arrow(observe.bottom(), goal.top());
        d.arrow(goal.right(), done.left(), "yes");
        d.arrow(goal.bottom(), stop.top(), "guardrail trip");
        // loop-back routes down the left margin, clear of act/observe
        d.elbow(pt(85, 200), "no, keep going",
                goal.left(), pt(85, 345), pt(85, 62), plan.left());
        return d.finish();
    }

    // ---- phase 5: three ways to put knowledge in front of a model ------

    private static String phase5() {
        var d = new DiagramRenderer(920, 400,
                "A skill and RAG both arrive through the context window and can be swapped per call; "
                        + "fine-tuning bypasses the context entirely because it is already in the weights.");
        // the three sources are siblings, so they get one shared width
        double source = 210;
        var skill = d.box(145, 70, STEP, source, "skill folder:", "instructions + resources");
        var loaded = d.box(420, 70, STEP, "loaded on demand");
        var rag = d.box(145, 200, STEP, source, "RAG:", "retrieved facts");
        var ctx = d.box(630, 135, STEP, "context window");
        var tuning = d.box(145, 330, STEP, source, "fine-tuning:", "baked into weights");
        var agent = d.box(830, 200, ENTRY, "agent");
        var out = d.box(830, 330, STEP, "output follows", "house style");

        d.arrow(skill.right(), loaded.left(), "on demand");
        d.arrow(loaded.right(), ctx.at(-1, -0.4));
        d.arrow(rag.right(), ctx.at(-1, 0.4));
        d.pill(389, 171, DiagramRenderer.INK_DIM, "facts, per query");
        d.arrow(ctx.right(), agent.at(-1, -0.4));
        d.elbow(pt(480, 330), "never in context",
                tuning.right(), pt(740, 330), pt(740, 200), agent.left());
        d.arrow(agent.bottom(), out.top());
        return d.finish();
    }

    // ---- phase 6: the integration boundary becomes a protocol ----------

    private static String phase6() {
        var d = new DiagramRenderer(930, 380,
                "One MCP client speaks JSON-RPC over stdio to two independent servers, each exposing "
                        + "its own tools, resources and prompts without being compiled into the agent.");
        var host = d.box(100, 185, ENTRY, "host: agent");
        var client = d.box(290, 185, STEP, "MCP client");
        var s1 = d.box(580, 85, STEP, "MCP server:", "receipts");
        var s2 = d.box(580, 290, STEP, "MCP server:", "trace stats");
        var r1 = d.box(800, 85, STEP, "tools, resources,", "prompts");
        var r2 = d.box(800, 290, STEP, "tools, resources,", "prompts");

        d.arrow(host.right(), client.left());
        d.arrow(client.right(), s1.at(-1, 0.4));
        d.pill(433, 140, DiagramRenderer.INK_DIM, "JSON-RPC over stdio");
        d.arrow(client.right(), s2.at(-1, -0.4));
        d.pill(433, 232, DiagramRenderer.INK_DIM, "JSON-RPC over stdio");
        d.arrow(s1.right(), r1.left());
        d.arrow(s2.right(), r2.left());
        return d.finish();
    }

    // ---- phase 7: every boundary is a typed handoff --------------------

    private static String phase7() {
        var d = new DiagramRenderer(940, 470,
                "The orchestrator fans one feature request out to a coder and a reviewer as typed Task "
                        + "cards, and merges the two TaskResults it gets back.");
        var ll = d.lifelines(40, 40, 140, 440,
                "User", "OrchestratorAgent", "CoderAgent", "ReviewerAgent");
        var user = ll[0];
        var orch = ll[1];
        var coder = ll[2];
        var reviewer = ll[3];

        d.message(user, orch, 115, false, "feature request");
        d.message(orch, coder, 172, false, "Task code.generate");
        d.message(coder, orch, 229, true, "TaskResult with artifacts");
        d.message(orch, reviewer, 286, false, "Task code.review");
        d.message(reviewer, orch, 343, true, "TaskResult with issues");
        d.message(orch, user, 400, true, "merged result");
        return d.finish();
    }

    // ---- phase 8: autonomy is where you put the humans -----------------

    private static String phase8() {
        var d = new DiagramRenderer(1060, 400,
                "Ticket to PR, with two named human gates that escalate instead of proceeding, and "
                        + "checkpointed state written off the main line.");
        var ticket = d.box(70, 185, ENTRY, "ticket");
        var plan = d.box(190, 185, STEP, "plan");
        var gate1 = d.diamond(350, 185, "GATE1:", "approve plan?");
        var escalate = d.box(350, 48, HALT, "escalate", "to human");
        var code = d.box(530, 185, STEP, "code");
        var tests = d.box(655, 185, STEP, "run tests");
        var gate2 = d.diamond(815, 185, "GATE2:", "approve PR?");
        var pr = d.box(995, 185, ENTRY, "open PR");
        var checkpoint = d.box(530, 330, STEP, "checkpoint jsonl");

        d.arrow(ticket.right(), plan.left());
        d.arrow(plan.right(), gate1.left());
        d.arrow(gate1.top(), escalate.bottom(), "no");
        d.arrow(gate1.right(), code.left(), "yes");
        d.arrow(code.right(), tests.left());
        d.arrow(tests.right(), gate2.left());
        d.arrow(gate2.right(), pr.left(), "yes");
        // both gates escalate to the same place - that is the point
        d.elbow(pt(600, 48), "no", gate2.top(), pt(815, 48), escalate.right());
        d.arrow(code.bottom(), checkpoint.top(), "state written");
        return d.finish();
    }

    // ---- phase 9: delegation only ever narrows -------------------------

    private static String phase9() {
        var d = new DiagramRenderer(940, 530,
                "A root credential with three scopes is delegated down to one scope with expiry capped "
                        + "at the parent's, and the token is passed out-of-band, never through the model.");
        var ll = d.lifelines(40, 30, 70, 500,
                "EndUser", "CredentialBroker", "OrchestratorAgent", "SpecializedAgent", "Tool");
        var user = ll[0];
        var broker = ll[1];
        var orch = ll[2];
        var specialized = ll[3];
        var tool = ll[4];

        d.message(user, broker, 120, false, "mintRoot,", "3 scopes");
        d.message(broker, orch, 188, true, "credential,", "3 scopes");
        d.message(orch, broker, 256, false, "delegate,", "request 1 scope");
        d.message(broker, specialized, 324, true, "credential, 1 scope,", "expiry capped at parent");
        d.message(specialized, tool, 392, false, "invoke, credential", "passed out-of-band");
        d.message(tool, specialized, 460, true, "result, token redacted", "before model sees it");
        return d.finish();
    }

    // ---- phase 10: the allowlist check happens before the socket ------

    private static String phase10() {
        var d = new DiagramRenderer(910, 400,
                "The trusted-origin check runs before any request is sent; only then is the agent card "
                        + "fetched, registered as a runtime capability, and paid for over x402.");
        var orch = d.box(110, 180, ENTRY, "OrchestratorAgent");
        var allow = d.diamond(300, 180, "origin in", "allowlist?");
        var skip = d.box(300, 48, HALT, "skip, no", "request sent");
        var get = d.box(520, 180, STEP, "GET /.well-known/", "agent-card.json");
        var card = d.box(770, 180, STEP, "AgentCard: name,", "capabilities, endpoint");
        var registry = d.box(770, 330, STEP, "runtime capability", "registry");
        var x402 = d.box(450, 330, STEP, "x402: 402 due,", "authorize, retry once");

        d.arrow(orch.right(), allow.left());
        d.arrow(allow.top(), skip.bottom(), "no");
        d.arrow(allow.right(), get.left(), "yes");
        d.arrow(get.right(), card.left(), "200 OK");
        d.arrow(card.bottom(), registry.top(), "registered");
        d.arrow(registry.left(), x402.right(), "call it");
        return d.finish();
    }

    // ---- phase 11: three gates, three different failures ---------------

    private static String phase11() {
        var d = new DiagramRenderer(950, 420,
                "Three independent gates on one chain - schema validity, confidence, circuit-breaker "
                        + "state - each failing in its own direction rather than into a shared error path.");
        var planner = d.box(95, 200, ENTRY, "PlannerAgent");
        var schema = d.diamond(265, 200, "schema gate");
        var invalid = d.box(265, 60, HALT, "SchemaValidation", "Exception");
        var confidence = d.diamond(530, 200, "confidence above", "threshold?");
        var abstain = d.box(530, 60, HALT, "abstain,", "pipeline stops");
        var breaker = d.diamond(810, 200, "circuit breaker", "state");
        var failFast = d.box(810, 60, HALT, "fail fast, no", "call attempted");
        var chain = d.box(810, 360, ENTRY, "CoderAgent then", "ReviewerAgent");

        d.arrow(planner.right(), schema.left());
        d.arrow(schema.top(), invalid.bottom(), "invalid");
        d.arrow(schema.right(), confidence.left(), "valid");
        d.arrow(confidence.top(), abstain.bottom(), "no");
        d.arrow(confidence.right(), breaker.left(), "yes");
        d.arrow(breaker.top(), failFast.bottom(), "OPEN");
        d.arrow(breaker.bottom(), chain.top(), "CLOSED");
        return d.finish();
    }

    // ---- phase 12: three layers, one number a CI gate can read ---------

    private static String phase12() {
        var d = new DiagramRenderer(760, 500,
                "One trace file feeds three independent measurement layers that fold into a single "
                        + "deterministic composite score, which a CI gate thresholds on.");
        var trace = d.box(390, 50, ENTRY, "run trace jsonl");
        var l1 = d.box(155, 175, STEP, "layer 1:", "task-level correctness");
        var l2 = d.box(390, 175, STEP, "layer 2:", "trajectory conformance");
        var l3 = d.box(625, 175, STEP, "layer 3:", "system health");
        var score = d.box(390, 300, STEP, "composite score 0-100");
        var gate = d.diamond(390, 430, "score above", "CI threshold?");
        var merge = d.box(610, 430, ENTRY, "merge");
        var block = d.box(170, 430, HALT, "block");

        d.arrow(trace.at(-0.7, 1), l1.at(0.4, -1));
        d.arrow(trace.bottom(), l2.top());
        d.arrow(trace.at(0.7, 1), l3.at(-0.4, -1));
        d.arrow(l1.at(0.4, 1), score.at(-0.7, -1));
        d.arrow(l2.bottom(), score.top());
        d.arrow(l3.at(-0.4, 1), score.at(0.7, -1));
        d.arrow(score.bottom(), gate.top());
        d.arrow(gate.right(), merge.left(), "yes");
        d.arrow(gate.left(), block.right(), "no");
        return d.finish();
    }

    // ---- phase 13: two rankings fused, then reranked -------------------

    private static String phase13() {
        var d = new DiagramRenderer(880, 400,
                "A keyword ranking and a vector ranking of the same query are fused by reciprocal rank "
                        + "fusion, reranked, and written into a ranked, bounded, decayed store.");
        var query = d.box(80, 140, ENTRY, "query");
        var bm25 = d.box(290, 65, STEP, "BM25 keyword rank");
        var vector = d.box(290, 215, STEP, "vector rank via", "HNSW-lite ANN");
        var fusion = d.box(500, 140, STEP, "reciprocal rank", "fusion");
        var rerank = d.box(750, 140, STEP, "cross-encoder-style", "rerank of shortlist");
        var store = d.box(750, 330, STEP, "MemoryStoreV2:", "ranked, bounded, decayed");
        var graph = d.box(290, 330, STEP, "GraphMemory:", "entity-relation triples");

        d.arrow(query.at(1, -0.5), bm25.at(-1, 0.4));
        d.arrow(query.at(1, 0.5), vector.at(-1, -0.4));
        d.arrow(bm25.right(), fusion.at(-1, -0.4));
        d.arrow(vector.right(), fusion.at(-1, 0.4));
        d.arrow(fusion.right(), rerank.left(), "shortlist");
        d.arrow(rerank.bottom(), store.top(), "top ranked");
        d.arrow(graph.right(), store.left(), "entity triples");
        return d.finish();
    }

    // ---- phase 14: hold the model constant, vary the levers ------------

    private static String phase14() {
        var d = new DiagramRenderer(830, 440,
                "Four levers are applied to a domain agent and compared against a baseline holding "
                        + "model, tools and temperature constant, so the levers are the only variable.");
        // four levers, one width - they are alternatives, not a hierarchy
        double lever = 216;
        var l1 = d.box(140, 55, STEP, lever, "lever 1: system prompt");
        var l2 = d.box(140, 130, STEP, lever, "lever 2: knowledge corpus");
        var l3 = d.box(140, 205, STEP, lever, "lever 3: tool selection");
        var l4 = d.box(140, 280, STEP, lever, "lever 4: guardrails");
        var agent = d.box(430, 170, ENTRY, "domain agent");
        var baseline = d.box(430, 380, STEP, "baseline agent,", "no levers");
        var constant = d.box(700, 280, STEP, "same model, same tools,", "same temperature");
        var conclusion = d.box(700, 85, STEP, "any behaviour", "difference is the levers");

        d.arrow(l1.right(), agent.at(-1, -0.6));
        d.arrow(l2.right(), agent.at(-1, -0.2));
        d.arrow(l3.right(), agent.at(-1, 0.2));
        d.arrow(l4.right(), agent.at(-1, 0.6));
        d.arrow(agent.at(1, 0.4), constant.at(-1, -0.5));
        d.arrow(baseline.at(1, -0.3), constant.at(-1, 0.5));
        d.arrow(constant.top(), conclusion.bottom(), "held constant");
        return d.finish();
    }

    // ---- the closer: five phases wired into one chain ------------------

    private static String combined() {
        var d = new DiagramRenderer(1020, 860,
                "The capstone chain: a credential that only narrows feeds a guarded agent loop, whose "
                        + "handoff must pass a schema gate and a confidence gate before anything is "
                        + "written to memory, and the score is computed from the run's own trace.");

        // regions first, so node fills and label pills paint over the tint
        d.group(40, 38, 300, 180, DiagramRenderer.TEAL, "phase 9 - identity");
        d.group(40, 248, 300, 310, DiagramRenderer.TEAL, "phase 4 & 3 - agent loop & tools");
        d.group(400, 248, 250, 310, DiagramRenderer.TEAL, "phase 11 - handoff");
        d.group(710, 390, 270, 200, DiagramRenderer.TEAL, "phase 13 - memory");
        d.group(710, 630, 270, 110, DiagramRenderer.TEAL, "phase 12 - evaluation");

        var mintRoot = d.box(190, 95, ENTRY, "mintRoot", "EndUser, 3 scopes");
        var delegate = d.box(190, 178, STEP, "delegate", "SummarizerAgent, 1 scope");

        var planStep = d.box(190, 298, STEP, "plan step");
        var guardrails = d.diamond(190, 398, "iteration cap +", "loop detection");
        var toolCall = d.box(190, 500, STEP, "credential-gated", "tool call");

        var schemaGate = d.diamond(525, 330, "schema gate");
        var confidenceGate = d.diamond(525, 450, "confidence gate");

        var memWrite = d.box(845, 450, STEP, "ranked memory write");
        var memRecall = d.box(845, 530, STEP, "decayed, bounded", "recall");

        var evaluation = d.box(845, 690, STEP, "deterministic", "composite score 0-100");

        var hardStop = d.box(670, 195, HALT, "hard stop,", "never coerced");
        var abstain = d.box(525, 600, HALT, "abstain,", "no memory write");
        var ciGate = d.diamond(845, 800, "CI gate");

        // within-region flow
        d.arrow(mintRoot.bottom(), delegate.top());
        d.arrow(planStep.bottom(), guardrails.top());
        d.arrow(guardrails.bottom(), toolCall.top());
        d.arrow(schemaGate.bottom(), confidenceGate.top(), "valid");
        d.arrow(memWrite.bottom(), memRecall.top());

        // cross-region flow - the seams the capstone exists to show.
        // This one leaves down the right margin rather than straight down the
        // column: a vertical at x=190 would run through the loop region's
        // title pill on its way in.
        d.elbow(pt(315, 230), "1-scope credential",
                delegate.right(), pt(315, 178), pt(315, 298), planStep.right());
        d.elbow(pt(330, 500), "handoff payload",
                toolCall.right(), pt(370, 500), pt(370, 330), schemaGate.left());
        d.elbow(pt(700, 278), "invalid",
                schemaGate.right(), pt(670, 330), hardStop.bottom());
        d.arrow(confidenceGate.right(), memWrite.left(), "above threshold");
        d.arrow(confidenceGate.bottom(), abstain.top(), "below threshold");
        // right of centre, for the same reason: the evaluation region's title
        // pill occupies the middle of the border this edge crosses
        d.arrow(memRecall.at(0.75, 1), evaluation.at(0.6, -1));
        d.pill(900, 600, DiagramRenderer.INK_DIM, "recall events");
        d.arrow(abstain.right(), evaluation.at(-1, -0.4));
        d.pill(665, 637, DiagramRenderer.INK_DIM, "still scored");
        d.arrow(evaluation.bottom(), ciGate.top(), "gate at 70");
        return d.finish();
    }
}
