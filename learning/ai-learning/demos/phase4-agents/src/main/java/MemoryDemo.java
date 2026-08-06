import java.util.List;

/**
 * Short-term memory (de messages-lijst binnen één run, zie AgentLoopDemo) vs
 * long-term memory (overleeft het process, via remember/recall).
 *
 * Draai dit commando TWEE KEER om het bewijs te zien:
 *   mvn -q compile exec:java -Dexec.mainClass=MemoryDemo
 * Run 1: geen long-term memory aanwezig -> agent onthoudt iets met remember().
 * Run 2: memory-store/memory-demo.json bestaat al -> NIEUW process, agent
 *        kent het feit alsnog, via recall() i.p.v. via de (inmiddels lege)
 *        messages-lijst van dit process.
 */
public class MemoryDemo {

    static final String FACT = "de belangrijkste dependency van dit project is Jackson";

    public static void main(String[] args) {
        OllamaClient client = new OllamaClient();
        String model = "llama3.2:3b";
        MemoryStore memory = new MemoryStore("memory-demo");
        List<Tool> tools = memory.asTools();

        if (!memory.hasAny()) {
            System.out.println("=== Run A (eerste keer): nog geen long-term memory aanwezig ===");
            AgentLoop loop = new AgentLoop(client, model, "Je bent een onderzoeksassistent.", tools,
                    new Guardrails(4, 0, true, null));

            String result = loop.run("Het volgende feit is waar: '" + FACT + "'. Onthoud dit met de remember-tool " +
                    "zodat je het later kan terugvinden. Bevestig daarna kort dat het onthouden is.");

            System.out.println("\nresultaat run A: " + result);
            System.out.println("\nDit was short-term memory in actie: binnen DEZE run 'weet' het model wat er net " +
                    "gezegd is (het staat in de messages-lijst van AgentLoop.run()). Zodra dit process stopt, is die " +
                    "lijst weg.");
            System.out.println("\nDraai deze demo NU NOG EEN KEER (zelfde commando) om te bewijzen dat de " +
                    "remember-tool wél overleeft -- dat is long-term memory.");
        } else {
            System.out.println("=== Run B (long-term memory al aanwezig van een vorige run) ===");
            AgentLoop loop = new AgentLoop(client, model, "Je bent een onderzoeksassistent.", tools,
                    new Guardrails(4, 0, true, null));

            String result = loop.run("Gebruik de recall-tool om te achterhalen wat de belangrijkste dependency van " +
                    "dit project is. Dit is NIET in dit gesprek verteld -- je kent het alleen als je het eerder " +
                    "hebt onthouden.");

            System.out.println("\nresultaat run B: " + result);
            System.out.println("\nDit bewijst het verschil: dit is een NIEUW Java-process (nieuwe, lege " +
                    "messages-lijst -- geen short-term memory van run A meer over) en toch kent het model het " +
                    "antwoord, via recall() uit memory-store/memory-demo.json. Dat is long-term memory: expliciet, " +
                    "tool-gemedieerd, en het overleeft het process.");
        }
    }
}
