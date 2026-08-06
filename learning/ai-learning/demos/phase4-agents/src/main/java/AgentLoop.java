import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * De generieke agent-loop: plan (model beslist) -> act (tool uitvoeren) ->
 * observe (resultaat terug het gesprek in) -> herhaal, tot het model geen
 * tool meer nodig heeft.
 *
 * Elke rol in fase4 (inbox-assistent, Researcher, PM, Architect, ...) is
 * DEZELFDE klasse, met een ander system-prompt + tool-subset + guardrails.
 * Dat is bewust: het gedragsverschil zit in configuratie, niet in code —
 * zelfde les als fase1's {@code ModelClient} (strategy pattern), nu
 * toegepast op agent-gedrag i.p.v. model-backend.
 *
 * De {@code messages}-lijst die binnen één {@link #run} opgebouwd wordt IS
 * het short-term memory: hij bestaat alleen zolang deze methode loopt en is
 * daarna weg. Long-term memory (zie {@code MemoryStore}) is bewust GEEN
 * onderdeel van deze klasse — het is gewoon een {@link Tool}, net als elke
 * andere. Dat houdt de engine generiek.
 */
class AgentLoop {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ---- observability: elke tool-call gestructureerd loggen (JSONL) -------
    // ÉÉN plek voor de hele engine, dus elke demo die AgentLoop gebruikt krijgt
    // dit gratis. In productie is dit je tracing-systeem (OpenTelemetry o.i.d.);
    // hier bewust minimaal, maar het principe staat: elke actie van de agent
    // moet achteraf reconstrueerbaar zijn, niet alleen leesbaar in de terminal.
    private static final Path TRACE_FILE = Path.of("trace.jsonl");

    private static void trace(int iteration, String tool, JsonNode args, String status, long latencyMs, String detail) {
        ObjectNode line = JSON.createObjectNode();
        line.put("ts", Instant.now().toString());
        line.put("iteration", iteration);
        line.put("tool", tool);
        line.set("args", args);
        line.put("status", status); // ok | error | denied | guardrail
        line.put("latencyMs", latencyMs);
        line.put("detail", detail == null ? "" : (detail.length() <= 300 ? detail : detail.substring(0, 300) + "..."));
        try {
            Files.writeString(TRACE_FILE, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.out.println("(kon trace niet wegschrijven: " + e.getMessage() + ")");
        }
    }

    /** Puur een noodstop van de demo-runner, GEEN guardrail-feature (die zit in {@link Guardrails}).
     * Zonder deze rail zou een echt ongeguarde loop (Guardrails.none()) tegen een levend model
     * letterlijk oneindig kunnen doorgaan — prima als leerpunt, niet prima om als proces
     * daadwerkelijk te laten hangen. Ruim boven wat een guardrail-demo ooit zou moeten halen. */
    private static final int RUNAWAY_SAFETY_CEILING = 25;

    private final OllamaClient client;
    private final String model;
    private final String systemPrompt;
    private final List<Tool> tools;
    private final Guardrails guardrails;

    AgentLoop(OllamaClient client, String model, String systemPrompt, List<Tool> tools, Guardrails guardrails) {
        this.client = client;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.guardrails = guardrails;
    }

    String run(String userTask) {
        ArrayNode messages = JSON.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userTask);
        return runLoop(messages);
    }

    private ArrayNode toolsSchema() {
        ArrayNode arr = JSON.createArrayNode();
        for (Tool t : tools) arr.add(t.schema(JSON));
        return arr;
    }

    private Tool find(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    private String runLoop(ArrayNode messages) {
        String lastCallKey = null;
        int iteration = 0;
        int tokensUsed = 0;

        while (true) {
            iteration++;

            if (guardrails.maxIterations() > 0 && iteration > guardrails.maxIterations()) {
                System.out.println("  [guardrail] max iteraties (" + guardrails.maxIterations() + ") bereikt — loop gestopt");
                trace(iteration, "_loop_", JSON.createObjectNode(), "guardrail", 0,
                        "max-iterations (" + guardrails.maxIterations() + ") bereikt");
                return null;
            }
            if (iteration > RUNAWAY_SAFETY_CEILING) {
                System.out.println("  [noodstop demo-runner] " + RUNAWAY_SAFETY_CEILING + " iteraties bereikt zonder " +
                        "guardrail-config — in een echt ongeguard systeem zou dit gewoon blijven doorgaan. We stoppen " +
                        "hier puur om deze demo niet daadwerkelijk oneindig te laten draaien.");
                return null;
            }

            OllamaClient.ChatResult result = client.chat(model, messages, toolsSchema());
            tokensUsed += result.tokensIn() + result.tokensOut();
            System.out.printf("  (iteratie %d, tokens in=%d uit=%d, cumulatief=%d)%n",
                    iteration, result.tokensIn(), result.tokensOut(), tokensUsed);

            if (guardrails.tokenBudget() > 0 && tokensUsed > guardrails.tokenBudget()) {
                System.out.println("  [guardrail] token-budget (" + guardrails.tokenBudget() + ") overschreden — loop gestopt");
                trace(iteration, "_loop_", JSON.createObjectNode(), "guardrail", 0,
                        "token-budget (" + guardrails.tokenBudget() + ") overschreden bij " + tokensUsed + " tokens");
                return null;
            }

            JsonNode assistantMsg = result.message();
            JsonNode toolCalls = assistantMsg.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return assistantMsg.path("content").asText().strip();
            }

            messages.add(assistantMsg);
            for (JsonNode call : toolCalls) {
                String name = call.path("function").path("name").asText();
                JsonNode args = call.path("function").path("arguments");
                String callKey = name + "(" + args + ")";

                if (guardrails.loopDetection() && callKey.equals(lastCallKey)) {
                    System.out.println("  [guardrail] loop gedetecteerd: identieke tool-aanroep twee keer op rij " +
                            "(" + callKey + ") — loop gestopt");
                    trace(iteration, name, args, "guardrail", 0, "loop-detection: identieke aanroep herhaald");
                    return null;
                }
                lastCallKey = callKey;

                long t0 = System.nanoTime();
                String toolResult = executeWithGuardrails(name, args);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.println("  [tool] " + callKey + " -> " + toolResult);
                String status = toolResult.startsWith("FOUT:") ? "error"
                        : toolResult.startsWith("GEWEIGERD:") ? "denied" : "ok";
                trace(iteration, name, args, status, ms, toolResult);
                messages.addObject().put("role", "tool").put("content", toolResult);
            }
        }
    }

    private String executeWithGuardrails(String name, JsonNode args) {
        Tool tool = find(name);
        if (tool == null) {
            return "FOUT: onbekende tool '" + name + "'";
        }
        if (tool.destructive() && guardrails.confirmHook() != null) {
            boolean confirmed = guardrails.confirmHook().confirm(name, args);
            if (!confirmed) {
                System.out.println("  [guardrail] confirm-hook blokkeerde destructieve tool '" + name + "'");
                return "GEWEIGERD: confirm-hook heeft deze destructieve aanroep niet goedgekeurd";
            }
        }
        try {
            return tool.executor().execute(args);
        } catch (IllegalArgumentException e) {
            return "FOUT: " + e.getMessage();
        }
    }
}
