import java.util.List;

/**
 * De naive agent-loop — ZONDER guardrails.
 *
 * De taak is met opzet onmogelijk te vervullen op de manier zoals gevraagd:
 * "blijf checken tot de inbox leeg is", maar er is geen tool om een bericht
 * als gelezen te markeren. {@code check_inbox} geeft dus altijd hetzelfde
 * resultaat terug. Dit is geen randgeval — dit is precies het soort
 * onder-gespecificeerde doel dat een echte agent-taak kan zijn.
 *
 * Zonder guardrails (zie {@code GuardrailsDemo} voor het vervolg) heeft de
 * loop geen enkele ingebouwde reden om te stoppen buiten de infra-noodstop
 * in {@link AgentLoop} — die noodstop is GEEN onderdeel van dit leerpunt,
 * hij bestaat puur zodat dit proces niet daadwerkelijk oneindig blijft
 * draaien.
 *
 * Draai met: mvn -q compile exec:java -Dexec.mainClass=AgentLoopDemo
 */
public class AgentLoopDemo {

    public static void main(String[] args) {
        OllamaClient client = new OllamaClient();
        String model = "llama3.2:3b";

        Tool checkInbox = new Tool(
                "check_inbox",
                "Controleer hoeveel ongelezen berichten er in de inbox staan.",
                Tool.emptyParams(),
                false,
                a -> "{\"unread\": 3}" // <- verandert nooit: er bestaat geen tool om een bericht als gelezen te markeren
        );

        AgentLoop loop = new AgentLoop(client, model,
                "Je bent een assistent die een inbox opruimt.",
                List.of(checkInbox),
                Guardrails.none());

        System.out.println("=== Naive agent-loop: plan -> act -> observe -> herhaal, ZONDER guardrails ===");
        System.out.println("Taak: blijf check_inbox aanroepen tot 'unread' op 0 staat.");
        System.out.println("Er is geen tool om een bericht te markeren als gelezen -- 'unread' kan dus NOOIT 0 worden.");
        System.out.println("Dit is short-term memory in actie: alles wat de agent 'onthoudt' zit in de messages-lijst");
        System.out.println("hieronder, en die lijst is weg zodra dit process stopt.\n");

        String result = loop.run(
                "Verwerk je inbox: blijf de tool check_inbox aanroepen totdat het aantal ongelezen berichten op 0 " +
                        "staat. Rapporteer daarna 'klaar'.");

        System.out.println("\neindresultaat: " + result);
        System.out.println("\nVergelijk dit met GuardrailsDemo, die exact dezelfde onmogelijke taak krijgt maar wél " +
                "netjes stopt.");
    }
}
