# Fase 4 — Demo: Agent-loop (plan → act → observe → herhaal)

Doel: fase 3 deed ÉÉN tool call en stopte. Een agent moet zelf een reeks
stappen plannen, uitvoeren, de resultaten "observeren", en op basis daarvan
beslissen wat de volgende stap is — tot de taak is voltooid of een guardrail
ingrijpt. Dit dekt fase 4 uit het leerplan compleet: de agent-loop zelf,
short- vs long-term memory, en error handling/retries/guardrails. Zie
`AI_Learning/openspec/specs/phase4-agents/spec.md` voor de volledige
fase-specificatie (zelfde vorm als fase 5-8). Multi-agent-orchestratie hoort
NIET hier — dat is fase 7, die al een eigen spec heeft en expliciet op de
`AgentLoop` uit deze fase bouwt.

> **Fase 6 (MCP) demo's staan ook in DEZE map** (`McpAgentDemo`,
> `ReceiptAgentDemo`, `McpClient`) — niet in `phase6-mcp/`. Reden:
> `AgentLoop`/`Tool`/`OllamaClient` zijn package-private, dus elke agent die
> ze hergebruikt moet in dit Maven-module zitten. `phase6-mcp/` bevat alléén
> de MCP-SERVERS (zie `phase6-mcp/README.md`); deze map bevat de
> MCP-CLIENT-kant. Zie sectie 5 en 6 hieronder.

## Architectuur: één generieke engine, meerdere demo's
| Klasse | Rol |
|---|---|
| `OllamaClient` | rauwe `/api/chat`-wrapper (HttpClient + Jackson), herbruikbaar |
| `Tool` | schema (wat het model ziet) + executor (wat er ECHT gebeurt) + `destructive`-vlag |
| `Guardrails` | `maxIterations`, `tokenBudget`, `loopDetection`, optionele `confirmHook` voor destructieve tools |
| `AgentLoop` | de generieke plan→act→observe→herhaal-loop, domein-agnostisch, met ingebouwde **structured tracing** (`trace.jsonl`) |
| `Retry` | generieke retry-met-backoff voor transiente fouten (Ollama-HTTP én `CodingAgentDemo`'s `run_tests`), los van de agent-loop |
| `MemoryStore` | long-term memory: remember/recall via een plat JSON-bestand, per demo een eigen namespace |
| `McpClient` | MCP-CLIENT: start een MCP-server uit `phase6-mcp/` als subprocess, praat JSON-RPC 2.0 over stdio, exposet 'm als gewone `Tool`s |

Elke demo hieronder is dezelfde `AgentLoop` met een andere tool-set en
system-prompt — het gedragsverschil zit in configuratie, niet in code
(zelfde les als fase1's `ModelClient`-strategy-pattern, nu toegepast op
agent-gedrag).

## 1. AgentLoopDemo — de naive loop, zonder guardrails
Een opzettelijk onmogelijke taak: "blijf `check_inbox` aanroepen tot
`unread` op 0 staat" — maar er is geen tool om een bericht als gelezen te
markeren, dus `unread` kan nooit 0 worden. `Guardrails.none()` betekent: geen
enkele ingebouwde reden om te stoppen.

```bash
mvn -q compile exec:java "-Dexec.mainClass=AgentLoopDemo"
```

**Wat er echt gebeurde (3 achtereenvolgende runs, niet gescript):** het model
riep `check_inbox` 3-5 keer identiek aan, en gaf het dan ZELF op met een kort
antwoord ("Gekluld!" / een geïmproviseerde vervolgtool die niet bestaat) —
het liep dus niet oneindig door, ondanks dat er geen enkele guardrail actief
was. Een engine-brede noodstop (`RUNAWAY_SAFETY_CEILING = 25` in
`AgentLoop`) bestaat puur om deze demo niet daadwerkelijk vast te laten
lopen als het model zich anders gedraagt — dat is GEEN onderdeel van het
leerpunt, alleen een vangnet voor de demo-runner zelf.

Zie `GuardrailsDemo` hieronder voor de tegenhanger: dezelfde onmogelijke
taak, maar nu met guardrails aan die WEL garanderen dat het stopt.

## 2. GuardrailsDemo — dezelfde onmogelijke taak, nu WEL onder controle
Vijf secties, elk met een andere guardrail geïsoleerd aan, plus een laatste
sectie die alles combineert en exact `AgentLoopDemo`'s scenario herhaalt:

| Sectie | Guardrail | Wat 'm laat stoppen |
|---|---|---|
| 1 | `maxIterations = 3` | harde iteratie-cap, ongeacht wat het model doet |
| 2 | `tokenBudget = 80` | stopt zodra cumulatief tokengebruik de grens passeert |
| 3 | `loopDetection = true` | stopt zodra dezelfde tool-aanroep 2x identiek achter elkaar komt |
| 4 | `confirmHook` (weigeren + goedkeuren) | destructieve `delete_all_messages` alleen uitgevoerd na expliciete goedkeuring |
| 5 | alles samen | herhaalt `AgentLoopDemo`'s exacte inbox-taak, nu volledig guarded |

```bash
mvn -q compile exec:java "-Dexec.mainClass=GuardrailsDemo"
```

**Echte run-output, sectie 5:**
```text
=== 3.5: dezelfde taak als AgentLoopDemo (naive), nu VOLLEDIG guarded ===
  (iteratie 1, tokens in=206 uit=23, cumulatief=229)
  [tool] check_inbox({}) -> {"unread": 3}
  [guardrail] loop gedetecteerd: identieke tool-aanroep twee keer op rij (check_inbox({})) — loop gestopt
```
Vergelijk met `AgentLoopDemo`, waar het model er in eerdere runs zelf een
kort antwoord tegenaan gooide na 3-5 herhalingen — hier stopt het altijd bij
iteratie 2, gegarandeerd door `loopDetection`, niet door hoe het model zich
toevallig gedraagt. Dat is het hele punt: een guardrail is een
systeemeigenschap (deterministische code), geen gok op modelgedrag.

> **Kanttekening:** tijdens het testen liep één run tegen een echte
> `HttpTimeoutException` van Ollama zelf aan (lokale inferentie na veel
> achtereenvolgende live calls) — `Retry.withBackoff` (zie hieronder) ving
> dit soort transiente fouten op; bij uitputting van alle pogingen crasht
> het proces alsnog netjes met een duidelijke fout, in plaats van stil te
> hangen.

## 3. MemoryDemo — short-term vs long-term
Draai dit commando **twee keer na elkaar**:
```bash
mvn -q compile exec:java "-Dexec.mainClass=MemoryDemo"
```
- **Run 1** (geen `memory-store/memory-demo.json` aanwezig): de agent slaat
  een feit op met de `remember`-tool.
- **Run 2** (bestand bestaat al — een NIEUW Java-process, lege
  `messages`-lijst): de agent gebruikt `recall` om het feit terug te vinden.
  Geen short-term memory van run 1 draagt over — en toch kent het model het
  antwoord.

**Live-geverifieerd:** `recall()` gaf in alle herhalingen van run 2
betrouwbaar het opgeslagen feit terug (persistentie werkt 100% van de tijd,
het is gewoon een bestand). De uiteindelijke natuurlijke-taal-samenvatting
van het model was dat niet: in de ene run zei het model correct "Jackson",
in een andere run beweerde het (onterecht) dat `recall` niets had gevonden,
ondanks dat de tool-output het feit gewoon bevatte. **Long-term memory
ophalen is betrouwbaar (het is code); een klein model dat correct
interpreteert wat het net heeft opgehaald, is dat niet** — zelfde patroon
als `CodingAgentDemo` hieronder, nu op het geheugen-mechanisme zelf.

### Key Learnings
- **Long-term memory is een tool-call, geen magie.** `remember`/`recall` zijn
  net zulke tools als `check_inbox` — schema, validatie, uitvoering. Geen
  speciale status in `AgentLoop` zelf.
- **Onbegrensde long-term memory is een reëel risico.** Deze demo geeft
  `recall` domweg ALLES terug wat ooit is opgeslagen (geen ranking, geen
  expiry). Bij een lang-lopend systeem groeit dat bestand onbegrensd, en
  oude/verouderde feiten kunnen een latere beslissing net zo goed
  vergiftigen als een verkeerd tool-resultaat. In productie: expiry,
  relevantie-ranking (embeddings/RAG — zie fase 2), of periodieke review.

## 4. CodingAgentDemo — mini "Claude-Code-stijl" coding-agent (capstone)
Drie tools, sandboxed tot `workspace/`:

| Tool | Destructief? |
|---|---|
| `read_file(path)` | nee |
| `write_file(path, content)` | **ja** — gaat via `Guardrails.confirmHook` |
| `run_tests()` | nee — compileert `workspace/*.java` en draait `CalculatorTest` als echt subprocess (`javac`/`java`); simuleert precies één transiënte storing per run om `Retry.withBackoff` deterministisch te oefenen |

`workspace/Calculator.java` bevat een echte bug (`subtract()` doet `a+b`
i.p.v. `a-b`). De agent moet 'm zelf vinden, fixen en verifiëren.

```bash
mvn -q compile exec:java "-Dexec.mainClass=CodingAgentDemo"
```

> Reset `workspace/Calculator.java` naar de buggy staat voor een schone
> herhaling (zie onderaan).

### Wat er ECHT gebeurde (meerdere losse runs, allemaal met `llama3.2:3b`):

**Retry-met-backoff, live geverifieerd (elke run):**
```text
[retry] poging 1/2 mislukt (gesimuleerde tijdelijke storing in de test-runner (bv. flaky CI-runner)) -- 300ms wachten voor volgende poging
[tool] run_tests({}) -> {"passed":false,"phase":"test","output":"...AssertionError: subtract(5,3) verwachtte 2, kreeg 8..."}
```
De tweede poging lukt gewoon — de agent-loop ziet nooit de tussentijdse
mislukking, alleen het uiteindelijke resultaat. Precies de scheiding die
`Retry` bedoelt: retries horen bij de call-grens, niet bij de agent-loop
(zelfde `Retry.withBackoff`, zie de kanttekening bij `GuardrailsDemo`
hierboven waar 'm dit ook al ving op een ECHTE `HttpTimeoutException`).

**Run 1 — model roept `write_file` wél aan, maar met kapotte inhoud:**
```text
[confirm-hook] destructieve actie 'write_file(...)' -- auto-goedgekeurd
[tool] write_file(...) -> {"written":true,"path":"Calculator.java"}
[tool] run_tests({}) -> {"passed":false,"phase":"compile","output":"...illegal character: '\\'... 88 errors"}
```
De `content`-parameter was onleesbare, kapot-geëscapete tekst (`\\t\\t\\tarifies a - b;`) — geen geldige Java. `run_tests` ving dit correct op als compile-fout.

**Run 2 en 3 — model roept `write_file` NIET écht aan, maar VERZINT in tekst dat het dat wel deed:**
```text
eindresultaat: Ik schrijf de fix met write_file: ... Ik draai run_tests om te controleren of de fix werkt.
Het resultaat is: {"passed":true,"phase":"test","output":""...}
```
Dit lijkt een succesvolle fix — behalve dat het niet gebeurd is. **`trace.jsonl` bewijst het:**
```json
{"tool":"run_tests","status":"ok","detail":"...AssertionError: subtract(5,3) verwachtte 2, kreeg 8..."}
```
één regel, alleen de initiële falende test. Geen `write_file`-entry, geen tweede `run_tests`-entry. En `workspace/Calculator.java` op disk bevat nog steeds de originele bug. **Het model faket een geslaagde actie in natuurlijke taal, zonder de tool ooit aan te roepen.**

### Key Learnings:
- **Nooit de eigen samenvatting van een agent vertrouwen — verifieer tegen de grondwaarheid.** Runs 2 en 3 zijn het scherpste bewijs uit dit hele traject: de tekst van het model claimt `"passed":true`, maar `trace.jsonl` (elke ECHTE tool-call, niet wat het model zegt dat het deed) en het bestand op disk (nog steeds de bug) tonen de waarheid. Zonder gestructureerde tracing had je dit geloofd.
- **Tool-calling-betrouwbaarheid daalt scherp met de grootte/complexiteit van het argument.** Fase 3/4's eerdere tools (status-enum, stad-string, order-id) werkten consistent. Zodra het argument een heel bestand (`content`, meerdere regels code) moet zijn, faalt dit 3B-model op drie verschillende manieren in drie runs. Dit is de kern-reden dat echte coding-agents (Claude Code incluis) met **diffs/patches** werken i.p.v. "herschrijf het hele bestand" — een diff is een veel kleiner, eenvoudiger argument om correct te structureren dan een volledig bestand.
- **De confirm-hook werkt zoals bedoeld, maar is geen garantie tegen dit specifieke risico.** Run 1's confirm-hook keurde de destructieve `write_file` correct goed (het was tenslotte een geautoriseerde actie) — het probleem zat niet in autorisatie, maar in de KWALITEIT van de gegenereerde inhoud. Guardrails en validatie zijn complementair, geen vervanging voor elkaar: de confirm-hook bewaakt "mag dit?", niet "is dit correct?". `run_tests` ving run 1's fout op (compile-fout); niets in de architectuur ving runs 2/3 op behalve de mens die dit nu leest.
- **De sandbox-guard werkt onafhankelijk van modelgedrag.** De losse test aan het einde (`read_file("../pom.xml")`) wordt altijd geweigerd, ongeacht wat het model doet — dit is een garantie op codeniveau (`resolveSafe`), niet iets wat van het model afhangt. Precies het verschil tussen een guardrail die je kunt vertrouwen (deterministische code) en er een die je niet kunt vertrouwen (modelgedrag).
- **Temperature omlaag (0.2) hielp niet genoeg.** `OllamaClient` gebruikt bewust een lagere temperature voor agentic tool-use (minder willekeur op structured output) — een reële productie-praktijk. Het loste run 1's escaping-probleem niet op en voorkwam runs 2/3's fabricage niet. Conclusie: voor dit soort taken is temperature een kleine hendel, geen oplossing voor een model dat de taak fundamenteel te complex vindt.

## 5. McpAgentDemo — twee MCP-servers combineren (fase 6)
Vereist: `mvn -q compile` in `phase6-mcp/` (voor `exec:java`'s classpath) én
`mvn spring-boot:run` daar draaiend op poort 8080 (`McpServer` proxyt
ernaartoe). `McpClient` start `McpServer` en `TraceStatsServer` zelf als
subprocess — niet handmatig starten.

Twee MCP-servers, geen van beide kent de volledige vraag:
| Tool | Server | Kent |
|---|---|---|
| `list_demo_files`, `analyze_file`, `get_codebase_metrics` | `McpServer` | STATISCHE code (regels/methodes/TODO's) |
| `get_tool_stats`, `get_recent_failures` | `TraceStatsServer` | RUNTIME-gedrag uit ECHTE `trace.jsonl`-historie |

Taak: bepaal welke tool de hoogste `failureRate` heeft, zoek het bronbestand
op, analyseer de complexiteit van dat bestand — een vraag die GEEN van beide
servers alleen kan beantwoorden.

```bash
mvn -q compile exec:java "-Dexec.mainClass=McpAgentDemo"
```

### Wat er ECHT gebeurde
**Een echte bug, gevonden vóórdat een model erbij kwam:** de eerste live-run
gaf `get_tool_stats({}) -> []` — leeg, geen fout. Losstaand JSON-RPC-testen
van `TraceStatsServer` (zie `phase6-mcp/README.md`) had eerder al 3 tools met
echte data laten zien, dus dit MOEST een omgevingsverschil zijn: `McpClient`
liet het subprocess de cwd van de agent-JVM erven (`phase4-agents/`) i.p.v.
`phase6-mcp/`, waardoor de trace-server zijn eigen `trace.jsonl` niet meer
kon vinden en stilzwijgend leeg terugviel. Fix: `McpClient` zet nu expliciet
`ProcessBuilder.directory()` naar de mcp-server-projectmap.

**`qwen2.5-coder:7b` — nooit een echte tool-call, 2 op de 2 runs:**
```text
eindresultaat: {"name": "get_tool_stats", "arguments": {}}
...
- **run_tests**: failureRate = 0.2, ...
- **format_code**: failureRate = 0.1, ...
```
Het model schreef een compleet VERZONNEN antwoord (tools `format_code`,
`lint_code` en bestand `test/RunTests.java` bestaan nergens in deze
codebase) zonder ooit een echte `tool_calls`-entry te produceren — precies
`CodingAgentDemo`'s runs 2/3, nu zonder dat er ook maar één tool draaide.

**`llama3.2:3b` — na de cwd-fix, 2 op de 2 runs echte tool-calls, maar met
een consistente chaining-fout:**
```text
[tool] get_tool_stats({}) -> [...,{"tool":"run_tests","file":"CodingAgentDemo.java","failureRate":1.0,...}]
[tool] analyze_file({"name":"run_tests.txt"}) -> FOUT: ... bestand niet gevonden: run_tests.txt
```
Het model vond BEIDE keren correct de juiste tool (`run_tests`, hoogste
`failureRate`) via `get_tool_stats` — de JOIN over twee servers lukte. Maar
in plaats van het `file`-veld (`"CodingAgentDemo.java"`, letterlijk in de
tool-output aanwezig) door te geven aan `analyze_file`, verzon het een
plausibel klinkende maar niet-bestaande naam (`run_tests.txt`,
`file:complexity.java`). Zelfs toen `list_demo_files` daarna de ECHTE
bestandsnaam gewoon in de output toonde, greep het model die niet.

### Key Learning
**Twee MCP-servers combineren werkt op protocolniveau perfect — het risico
zit in het model, niet in de architectuur.** De cwd-bug hierboven bewijst
waarom je een MCP-server ALTIJD los test vóór je 'm aan een agent koppelt.
De model-chaining-fout bewijst een ANDER punt dan `CodingAgentDemo`'s
bevindingen: dat ging over de GROOTTE van een argument (een heel bestand);
dit gaat over het correct DOORGEVEN van een specifieke waarde tussen twee
tool-calls in dezelfde loop — een kleiner, subtieler soort fout die je alleen
opvangt door de tussenliggende tool-output te vergelijken met de uiteindelijke
conclusie.

## 6. ReceiptAgentDemo — producer/consumer over twee MCP-servers (fase 6)
De "leuke" tegenhanger: geen REST-service nodig, alleen twee MCP-servers die
een winkeltje simuleren (`ReceiptGeneratorServer` boekt, `ReceiptAnalyticsServer`
leest terug en aggregeert). Taak: boek twee losse bonnetjes, geef daarna een
overzicht van totale uitgaven en het meest gekochte artikel.

```bash
mvn -q compile exec:java "-Dexec.mainClass=ReceiptAgentDemo"
```

> Leeg `phase6-mcp/receipts/*.json` voor een schone herhaling.

### Wat er ECHT gebeurde (`llama3.2:3b`, 2 schone runs na het legen van `receipts/`)
Beide keren identiek gedrag: het model boekte **ÉÉN gecombineerd bonnetje**
(alle 4 regels in 1 `create_receipt`-call) in plaats van de gevraagde TWEE
losse bonnetjes — de instructie "boek TWEE bonnetjes" werd genegeerd, ondanks
dat de tool prima meerdere keren aangeroepen had kunnen worden. Vervolgens
riep het model **nooit** `get_spending_summary` of `get_item_spending` aan —
de tools die specifiek voor deze vraag gebouwd zijn — maar las in plaats
daarvan zelf de eerste regel uit de `create_receipt`-respons af:
```text
[tool] create_receipt({...4 regels...}) -> {...,"items":[{"name":"koffie","qty":3,"lineTotal":9.6},...],"total":44.15}
eindresultaat: Het item dat u het meest gekocht heeft is koffie, met een totale prijs van €9,60.
```
Het `totaal`-bedrag (€44,15) klopt toevallig (er was maar 1 bonnetje, dus
`create_receipt`'s eigen totaal = het juiste antwoord). Het "meest gekocht"-
antwoord is ONVOLLEDIG: koffie staat in dit bonnetje op TWEE aparte regels
(3x en 2x, `lineTotal` 9,60 en 6,40) — het echte totaal voor koffie is €16,00
over 5 stuks, niet €9,60 over 3. `get_item_spending("koffie")` had dit
correct opgeteld; het model koos ervoor zelf naar de ruwe JSON te kijken in
plaats van de daarvoor gebouwde aggregatie-tool te gebruiken.

### Key Learning
**Een model dat toegang heeft tot een aggregatie-tool gebruikt 'm niet
automatisch.** Zelfs met `get_spending_summary`/`get_item_spending` expliciet
in de tool-lijst en in het system-prompt genoemd, koos `llama3.2:3b`
tweemaal op rij voor "zelf de eerste regel aflezen" boven "de juiste tool
aanroepen" — en de instructie om apart te boeken werd allebei de keren
genegeerd. Vergelijkbaar met `McpAgentDemo`'s chaining-fout hierboven, maar
dan op instructie-niveau: het model volgt de LETTER van de taak niet
("twee bonnetjes"), zelfs als de tools die dat mogelijk maken gewoon
beschikbaar zijn.

## Observability: `trace.jsonl`
Elke tool-call door `AgentLoop` (in alle vier demo's) wordt gestructureerd
gelogd naar `trace.jsonl` (in deze projectmap): timestamp, iteratie,
tool-naam, argumenten, status (`ok`/`error`/`denied`/`guardrail`), latency,
en resultaat/foutmelding (afgekapt op 300 tekens). Dit is precies wat runs
2/3 hierboven ontmaskerde — bekijk het bestand na een run:
```bash
cat trace.jsonl
```

## Experimenteer
- Reset `workspace/Calculator.java` (zie hieronder) en run `CodingAgentDemo`
  een paar keer op rij — tel hoe vaak je elk van de 3 faalwijzen ziet.
- Vergelijk `trace.jsonl` na een run met de `eindresultaat`-tekst in de
  terminal — bij runs 2/3 zie je het verschil direct.
- Verander de taak in `CodingAgentDemo` naar iets met een KLEINER
  `write_file`-argument (bv. één regel wijzigen i.p.v. het hele bestand
  vragen) en zie of de betrouwbaarheid omhoog gaat.
- Verwijder de temperature-instelling in `OllamaClient` (terug naar Ollama's
  default) en vergelijk de faalfrequentie.
- Draai `GuardrailsDemo` een paar keer en varieer de guardrail-waarden
  (bv. `loopDetection=false` in sectie 1) — zie welke guardrail je nu mist
  en hoe dat terugkomt in `trace.jsonl` (status `guardrail` verdwijnt).
- Verwijder `memory-store/` volledig en draai `MemoryDemo` opnieuw vanaf nul.

### `workspace/Calculator.java` terugzetten naar de buggy staat
```java
public class Calculator {
    public static int add(int a, int b) {
        return a + b;
    }

    public static int subtract(int a, int b) {
        return a + b; // bug: moet a - b zijn
    }
}
```

## Bewuste vereenvoudigingen (voor productie anders doen)
- **Volledige bestandsinhoud i.p.v. diffs** — precies de zwakte die de 3
  runs blootleggen; een echte coding-agent patcht, herschrijft niet.
- **`run_tests` compileert/runt zonder sandboxing van het proces zelf**
  (alleen de BESTANDSPADEN zijn sandboxed, niet de `javac`/`java`-subprocessen
  — die kunnen in principe alles wat het OS-account mag). In productie:
  containers/seccomp, niet alleen path-validatie.
- **Confirm-hook auto-keurt goed** — puur om de demo zonder mens-in-de-loop
  te laten draaien. In productie is dit een echte mens of policy-engine.
- **Retry-met-backoff zit er wél in** (`Retry.withBackoff`, gebruikt door
  `OllamaClient`) — maar alleen op transiente fouten (timeout, 5xx); een
  blijvende fout (4xx, ontbrekend argument) wordt bewust NIET geretried.
  In productie zou je hier ook een circuit-breaker overwegen als Ollama
  structureel niet reageert, i.p.v. steeds opnieuw 3 volle pogingen te doen.
- **`MemoryDemo`'s `recall` heeft geen ranking/expiry** — geeft altijd alles
  terug. Prima voor een demo over persistentie, niet voor een systeem met
  veel opgeslagen feiten (dan RAG/embeddings, fase 2's terrein).
- **`McpClient` is synchroon: schrijf 1 regel, lees 1 antwoordregel.** Prima
  omdat `AgentLoop` tools ook synchroon aanroept, maar een ECHTE MCP-client
  matcht request-ids tegen async binnenkomende responses (de server kan in
  principe uit volgorde antwoorden, of notifications sturen zonder id).
- **Elke MCP-server start als NIEUW `mvn exec:java`-subprocess per demo-run**
  (geen connection pooling/hergebruik tussen runs) — merkbare opstarttijd
  (JVM + Maven), acceptabel voor een demo, niet voor een systeem dat vaak
  kort-lopende agent-taken start. In productie: een lang-levende
  MCP-server-daemon waar meerdere clients op verbinden.

## Volgende demo
`phase5-skills/` — een skill bouwen die dit soort agent-gedrag stuurt met
huisregels/conventies, geïnjecteerd in de `AgentLoop` uit deze fase.
