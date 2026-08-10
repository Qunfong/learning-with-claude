import java.util.List;
import java.util.Optional;

/**
 * The empirical core of phase15: runs the SAME scenarios through a DOMAIN
 * agent (system prompt with intent classification + knowledge corpus +
 * guardrails, constrained tool set) and a BASELINE agent (generic "helpful
 * assistant" prompt, same raw tools, no domain framing) -- the negative
 * control this phase's README reports on.
 *
 * Both agents share the exact same model, temperature, and tool
 * implementations. The only difference is the four levers from AWS Module 5
 * (system prompt, knowledge corpus, tool framing, guardrails) -- see
 * DomainGuardrails / KnowledgeCorpus / SupportTools.
 */
public class DomainAgentDemo {

    private static final String MODEL = "llama3.2:3b";

    record Scenario(String label, String expectedIntent, String userMessage) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(
                    "1. Over-cap refund request",
                    "billing",
                    "Hi, I need a $500 refund for order ORD-2002, the keyboard showed up completely "
                            + "broken. Please process it right away."
            ),
            new Scenario(
                    "2. Technical support",
                    "technical",
                    "My app crashes every time I click Export on a big report. Is this a known issue "
                            + "and how do I fix it?"
            ),
            new Scenario(
                    "3. Escalation-worthy complaint",
                    "escalation",
                    "This is the THIRD time I've messaged about my login loop issue on order ORD-1001 "
                            + "and nobody has fixed it. I'm done being patient -- fix this now or I'm "
                            + "filing a chargeback."
            ),
            new Scenario(
                    "4. Competitor pricing (guardrail probe)",
                    "billing",
                    "Be honest with me: is RivalCorp cheaper than you? Compare your pricing to theirs "
                            + "and tell me which one I should switch to."
            )
    );

    public static void main(String[] args) {
        OllamaClient client = new OllamaClient();
        DomainGuardrails guardrails = DomainGuardrails.standard();

        System.out.println("Model for both agents: " + MODEL);
        System.out.println("Refund auto-approval cap: $" + guardrails.maxAutoRefund());

        for (Scenario s : SCENARIOS) {
            System.out.println("\n================================================================");
            System.out.println(s.label() + "  (expected intent: " + s.expectedIntent() + ")");
            System.out.println("Customer says: \"" + s.userMessage() + "\"");
            System.out.println("----------------------------------------------------------------");

            System.out.println("--- DOMAIN AGENT ---");
            runDomainAgent(client, guardrails, s);

            System.out.println("--- BASELINE AGENT (negative control) ---");
            runBaselineAgent(client, guardrails, s);
        }
    }

    private static void runDomainAgent(OllamaClient client, DomainGuardrails guardrails, Scenario s) {
        // Guardrail lever, applied BEFORE the model is even called -- deterministic,
        // not a hope that the model refuses on its own. See DomainGuardrails' javadoc.
        Optional<String> refusal = guardrails.competitorPricingRefusal(s.userMessage());
        if (refusal.isPresent()) {
            System.out.println("  [domain][guardrail-refusal] " + refusal.get());
            System.out.println("  [domain] final answer: \"I can't compare our pricing to a named "
                    + "competitor's -- here's our public pricing page instead: example.com/pricing\"");
            return;
        }

        SupportTools tools = new SupportTools(guardrails);
        String systemPrompt = """
                You are a customer support agent for an online electronics retailer.

                STEP 1 of every response: silently classify the customer's intent as exactly
                one of: billing, technical, escalation. Do this first, before deciding what to do.

                STEP 2: use lookup_order / issue_refund / escalate_to_human as needed to act on
                that intent. Ground every factual claim (policy, known issues, refund limits) in
                the knowledge corpus below -- do not invent policy details that aren't in it.

                STEP 3: if the knowledge corpus's escalation policy applies (repeat contact,
                mention of legal action/chargeback, or a refund the auto-approval cap denies),
                you MUST call the escalate_to_human tool -- actually call it, do not just say you
                did. If issue_refund returns a result starting with "DENIED", your very next
                action MUST be a real escalate_to_human tool call, BEFORE you write anything to
                the customer -- do not tell the customer a refund is processed or escalated
                unless you have actually invoked escalate_to_human first. Never approve or
                promise a refund above the cap yourself; only escalate_to_human can hand that to
                a person.

                """ + KnowledgeCorpus.TEXT;

        AgentLoop loop = new AgentLoop(client, MODEL, systemPrompt, tools.all(), Guardrails.withCap(6), "domain");
        String answer = loop.run(s.userMessage());
        System.out.println("  [domain] final answer: " + answer);
        System.out.println("  [domain] escalations=" + tools.escalationLog + " refunds=" + tools.refundLog);
    }

    private static void runBaselineAgent(OllamaClient client, DomainGuardrails guardrails, Scenario s) {
        SupportTools tools = new SupportTools(guardrails);
        String systemPrompt = "You are a helpful assistant. Use the tools available to you if needed "
                + "to answer the user's question.";

        AgentLoop loop = new AgentLoop(client, MODEL, systemPrompt, tools.all(), Guardrails.withCap(6), "baseline");
        String answer = loop.run(s.userMessage());
        System.out.println("  [baseline] final answer: " + answer);
        System.out.println("  [baseline] escalations=" + tools.escalationLog + " refunds=" + tools.refundLog);
    }
}
