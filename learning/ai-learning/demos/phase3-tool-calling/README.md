# Fase 3 — Demo: Anatomie van een tool call

Doel: laten zien wat een "tool call" ECHT is — geen magie, geen framework
ertussen. Het model produceert gestructureerde JSON (naam + argumenten) i.p.v.
vrije tekst; jouw code beslist of, en hoe, die aanroep wordt uitgevoerd.

## Wat het doet
Roept een lokaal model aan via Ollama's `/api/chat` (model met `tools`-capability,
zoals `llama3.2:3b`) en doorloopt de volledige loop:

1. **Schema geven** — een JSON-schema van de beschikbare tool(s): naam, beschrijving, parameters (net als een method-signature, maar dan voor het model leesbaar)
2. **Model beslist** — het model roept de tool niet aan, het antwoordt met een STRUCTURED verzoek (`tool_calls`: naam + argumenten als JSON)
3. **Valideren vóór uitvoeren** — argumenten controleren voordat je Java-code ze gebruikt (nooit blind vertrouwen op wat het model verzint)
4. **Resultaat terug de conversatie in** — als `role: "tool"` bericht, met de ruwe data
5. **Model vat samen** — tweede modelaanroep zet het tool-resultaat om in natuurlijke taal

Er zit geen agent-framework tussen — de hele loop is met de hand geschreven
(rauwe `HttpClient` + Jackson), zodat elke stap zichtbaar blijft.

## Runnen
Vereist Ollama lokaal met een tool-capable model:
```bash
ollama pull llama3.2:3b
ollama serve
mvn -q compile exec:java "-Dexec.mainClass=ToolCallingDemo"
```

### Voorbeeld run-output:
```text
=== Tool-schema dat we het model geven ===
[ {
  "type" : "function",
  "function" : {
    "name" : "get_orders",
    "description" : "Haal orders op uit de database, gefilterd op status.",
    "parameters" : {
      "type" : "object",
      "properties" : {
        "status" : { "type" : "string", "enum" : [ "shipped", "cancelled", "open" ], "description" : "Filter op deze status." }
      },
      "required" : [ "status" ]
    }
  }
} ]

=== Stap 0: vraag zonder tool-noodzaak (contrast) ===
         (tokens: in=187 uit=17)
model riep TOCH een tool aan, hoewel de vraag er niets mee te maken heeft:
  {"index":0,"name":"get_orders","arguments":{"status":"open"}}
(bekende beperking van kleine modellen: zodra tools beschikbaar zijn, grijpen ze
 soms te snel -- dit is precies waarom validatie in stap 3 niet optioneel is)

=== Stap 1: user-vraag ===
Hoeveel orders staan er nog open, en wat is het totale bedrag? Gebruik de tool.

=== Stap 2: model vraagt om een tool aan te roepen ===
         (tokens: in=199 uit=17)
naam      : get_orders
argumenten: {"status":"open"}  <- dit is STRUCTURED OUTPUT, geen vrije tekst

=== Stap 3: validatie voordat we uitvoeren ===
-- valide aanroep --
resultaat: [{"id":1,"customer":"Jansen","amount":149.5,"status":"open"},{"id":3,"customer":"de Vries","amount":89.0,"status":"open"}]
-- gesimuleerde hallucinatie: model verzint status 'pending' (bestaat niet) --
geweigerd: ongeldige status 'pending', moet één van [shipped, cancelled, open] zijn  <- precies waarom je nooit blind mag uitvoeren

=== Stap 4: tool-resultaat terug de conversatie in ===
(role="tool", content=ruwe JSON — het model leest dit als context, niet als code)

=== Stap 5: model vat het resultaat samen in natuurlijke taal ===
         (tokens: in=151 uit=24)
eindantwoord: De orders die nog open staan, zijn 2. Het totale bedrag is €238,5.
```

### Key Learnings:
- **Een tool call is gewoon JSON.** `message.tool_calls[0].function` = `{name, arguments}`. Er is geen "het model belt een functie" — het model *genereert tekst die toevallig een JSON-schema volgt*. Alles daarna (parsen, valideren, uitvoeren) is jouw code, geen modelmagie.
- **Het schema is de enige communicatie over wat mogelijk is.** Het model kent je Java-methode `executeTool` niet — het kent alleen de `description` en `parameters` uit het schema. Slechte beschrijving = slechte tool-keuze door het model. Dit is het eerste ontwerp-oppervlak dat je in de hand hebt.
- **Kleine modellen grijpen soms te snel naar een tool** (stap 0): met `llama3.2:3b` riep het model `get_orders` aan voor een vraag die er niets mee te maken had ("wat is een token"). Dit is een bekende beperking van kleinere modellen — een sterker model (of een betere `description`/system-prompt) roept 'm minder snel onnodig aan, maar de garantie blijft: **valideer altijd**, vertrouw nooit blind op de intentie van het model.
- **Validatie is niet optioneel — dat IS de grens tussen model en systeem.** Het model kan een niet-bestaande status verzinnen (`"pending"` i.p.v. `open/shipped/cancelled`) — precies zoals het een niet-bestaand bestandspad of SQL-kolom kan verzinnen. `executeTool` gooit een `IllegalArgumentException` vóórdat er ook maar iets wordt uitgevoerd. Dit is dezelfde discipline als input-validatie op een REST-endpoint: vertrouw nooit de aanroeper, ook niet als de aanroeper een LLM is.
- **De tool-loop is stateless per call — jij houdt de geschiedenis bij.** Elke `/api/chat`-aanroep krijgt de VOLLEDIGE `messages`-lijst opnieuw mee (user → assistant met tool_calls → tool-resultaat). Het model "onthoudt" niets tussen calls; de illusie van een doorlopend gesprek zit in jouw `ArrayNode messages` die je steeds langer maakt en opnieuw meestuurt.
- **Tokengebruik groeit met elke beurt.** Stap 2 (`in=199`) en stap 5 (`in=151`, na een kortere prompt maar met tool-resultaat erin) laten zien dat elke extra beurt in de loop opnieuw de hele geschiedenis door de tokenizer haalt — bij lange agent-loops is dit een reële kostenfactor, niet alleen een latency-vraag.

## Experimenteer
- Verander `question` in `main` naar iets dat een andere/geen tool nodig heeft, en zie hoe het model reageert.
- Voeg een tweede tool toe (bv. `get_customer(name)`) en zie hoe het model kiest tussen twee opties op basis van hun `description`.
- Verwijder de validatie in `executeTool` en voer de "pending"-aanroep alsnog uit — zie wat er misgaat (lege/verkeerde resultaten, of een crash) zonder de guard.
- Probeer een sterker model (`llama3.1:8b` of een hosted model) en vergelijk of stap 0 dan wél schoon blijft.

## Bewuste vereenvoudigingen (voor productie anders doen)
- **In-memory "database"** (`List<Order>`) — geen echte databron; het punt is de tool-loop, niet de datalaag.
- **Eén tool, één argument** — echte agents hebben vaak meerdere tools met geneste schema's; het principe (schema → model beslist → valideer → voer uit) blijft identiek.
- **Geen retry/backoff, geen streaming** — zelfde vereenvoudiging als in de eerdere fases, om de focus op de tool-loop te houden.
- **Geen system-prompt om overmatig tool-gebruik te temperen** — in productie zou je hier een system-message toevoegen die expliciet zegt wanneer de tool WEL en NIET gebruikt moet worden.

## Volgende demo
`phase4-agents/` — de tool-loop uitbreiden naar een echte agent-loop (plan → act → observe → herhaal).
