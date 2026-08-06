import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CODING state's agent -- capability {@code code.generate}, per phase7's
 * CoderAgent spec ("Calls: Phase 4 agent loop (generate code); Outputs: Java
 * class"), reimplemented self-contained here (no import from
 * demos/phase7-multi-agent/).
 *
 * {@link Task#input()} is expected to already contain everything the model
 * needs (ticket, plan, current file content, optional test-failure feedback
 * from a RETRY) -- assembled by {@link AutonomousPipeline}. The model is
 * asked to return the COMPLETE new file content as plain text (see
 * {@link OllamaClient}'s header comment for why this avoids tool-calling
 * with a large argument).
 */
class CoderAgent implements A2AAgent {

    private static final Pattern FENCE = Pattern.compile("```(?:java)?\\s*\\n?([\\s\\S]*?)```");

    private final OllamaClient client;
    private final String model;

    CoderAgent(OllamaClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("CoderAgent", List.of("code.generate"));
    }

    @Override
    public TaskResult handle(Task task) {
        String system = """
                You are a Java coding agent. You will be given a ticket, an implementation
                plan, and the CURRENT full content of a Java file (or a note that it's a new
                file). Return the COMPLETE new file content implementing the plan exactly.
                Output ONLY raw Java source code -- no markdown code fences, no explanation,
                no commentary before or after the code. Do not rename or change the signature
                of any existing public method unless the ticket explicitly asks you to --
                other code depends on the existing contract.
                """;

        long t0 = System.nanoTime();
        OllamaClient.ChatResult r = client.chat(model, system, task.input());
        long ms = (System.nanoTime() - t0) / 1_000_000;

        String code = stripFences(r.text());
        if (code.isBlank()) {
            return new TaskResult("failed", "", List.of("model returned empty code"),
                    r.tokensIn(), r.tokensOut(), ms);
        }
        return new TaskResult("done", code, List.of(), r.tokensIn(), r.tokensOut(), ms);
    }

    /** Small local models routinely wrap output in ```java fences despite instructions not to -- strip defensively. */
    static String stripFences(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = FENCE.matcher(text);
        if (m.find()) {
            return m.group(1).strip();
        }
        return text.strip();
    }
}
