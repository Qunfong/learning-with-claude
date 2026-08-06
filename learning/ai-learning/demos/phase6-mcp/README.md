# Fase 6 — Demo: MCP (Model Context Protocol)

Doel: fase 4/5 gaven de agent tools/skills die LOKAAL in hetzelfde Java-proces
draaiden. Fase 6 haalt die grens weg: een MCP-server is een APART proces
(mogelijk een andere taal, andere machine) dat een model via een
gestandaardiseerd protocol (JSON-RPC 2.0) kan aanroepen. Zie
`AI_Learning/openspec/specs/phase6-mcp/spec.md` voor de volledige
fase-specificatie.

Bewust **Option B** uit de spec: het protocol met de hand geïmplementeerd
(newline-delimited JSON-RPC 2.0 over stdio), geen MCP Java SDK. Het punt van
deze fase is begrijpen wat een client/server elkaar sturen, niet een library
aanroepen die dat verbergt.

## Architectuur: twee onafhankelijke server-paren

| Server | Rol | Kent |
|---|---|---|
| `CodeAnalysisApplication` (Spring Boot, poort 8080) | de ECHTE Java-service | niets van MCP — gewoon REST |
| `McpServer` | MCP-wrapper om de REST-service | STATISCHE code (regels/methodes/TODO's) |
| `TraceStatsServer` | tweede, onafhankelijke MCP-server | RUNTIME-gedrag (`phase4-agents/trace.jsonl`, ECHTE eerdere agent-runs) |
| `ReceiptGeneratorServer` | MCP-server, PRODUCER | het menu (`MenuCatalog`), boekt bonnetjes als JSON-bestand |
| `ReceiptAnalyticsServer` | MCP-server, CONSUMER | leest dezelfde bonnetjes terug, aggregeert |

Geen enkele server kent de HELE vraag die de agent straks stelt — dat is
precies het punt: multi-server MCP levert meer op dan één server omdat elke
server klein en single-purpose blijft, en de COMBINATIE bij de agent zit
(zie `phase4-agents/McpAgentDemo.java` en `ReceiptAgentDemo.java`).

## Draaien

**1. De REST-service (verplicht voor `McpServer`, niet voor de andere 3):**
```bash
mvn spring-boot:run
```
Blijft draaien op poort 8080. Smoke-test:
```bash
curl http://localhost:8080/metrics
```

**2. Een MCP-server los testen (stdio, JSON-RPC-regel per regel):**
```bash
echo {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}} | mvn -q exec:java
echo {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"analyze_file","arguments":{"name":"AgentLoop.java"}}} | mvn -q exec:java
```
Default main-class is `McpServer`. Voor de andere drie:
```bash
mvn -q exec:java -Dexec.mainClass=TraceStatsServer
mvn -q exec:java -Dexec.mainClass=ReceiptGeneratorServer
mvn -q exec:java -Dexec.mainClass=ReceiptAnalyticsServer
```

**3. Een agent die BEIDE servers van een pair combineert:** zie
`../phase4-agents/README.md` — `McpAgentDemo` en `ReceiptAgentDemo` starten
deze servers zelf als subprocess via `McpClient`, je hoeft ze niet handmatig
te starten (behalve de Spring Boot-service voor `McpAgentDemo`).

## Wat er ECHT gebeurde (McpServer + REST-service, live geverifieerd)
Alle 4 REST-endpoints + alle 4 MCP-tools zijn met de hand getest via rauwe
`curl`/JSON-RPC-regels vóórdat een model erbij kwam:
```text
GET /metrics          -> {"files":28,"totalLines":3578,"totalMethods":61,"totalTodos":4}
POST /analyze          -> {"file":"...AgentLoop.java","lines":179,"methods":5,"todos":0}
GET /files/DoesNotExist.java -> 404 "bestand niet gevonden: DoesNotExist.java"
```
`tools/call` → `analyze_file` gaf exact dezelfde data terug via de MCP-laag —
de vertaling REST → MCP voegt geen fouten toe, wat je zou willen van een dunne
proxy-laag.

## Een ECHTE bug die alleen zichtbaar werd door een agent erbij te halen
`TraceStatsServer`/`ReceiptGeneratorServer`/`ReceiptAnalyticsServer` resolven
hun bestandspaden relatief aan "waar draai ik vanuit" (`Path.of("")`). Los
getest (handmatig `mvn exec:java` VANUIT `phase6-mcp/`) werkte dit perfect.
Zodra `phase4-agents/McpClient` deze servers als subprocess start, erft dat
subprocess **de cwd van de agent-JVM** (`phase4-agents/`), niet
`phase6-mcp/` — en de pad-heuristiek viel stil terug op een verkeerd pad.
Resultaat: `get_tool_stats` gaf `[]` terug. Geen crash, geen foutmelding —
gewoon stilzwijgend leeg. Precies het soort fout dat **alleen** opvalt als
je de ECHTE tool-output controleert in plaats van het model te geloven (zie
`CodingAgentDemo`'s runs 2/3 in fase4 voor hetzelfde principe, nu op de
MCP-laag zelf). Fix: `McpClient` zet nu expliciet `ProcessBuilder.directory()`
naar de map van de meegegeven pom, ongeacht vanuit welke map de agent draait.

## Key Learning
**Een MCP-server die "leeg" teruggeeft is niet per se correct leeg.** Zonder
een onafhankelijke, met-de-hand-uitgevoerde JSON-RPC-call (stap 2 hierboven)
als referentiepunt was deze cwd-bug onopgemerkt gebleven — de agent-demo zelf
gaf gewoon een (verkeerde) conclusie zonder te klagen. Test een MCP-server
ALTIJD eerst los van elke agent/model, met een eigen JSON-RPC-regel, vóórdat
je 'm aan een LLM koppelt.

## Volgende demo
`phase7-multi-agent/` (nog te bouwen) — twee agents (bv. "coder" + "reviewer")
die via A2A samenwerken, bouwend op deze fase's `AgentLoop` + MCP-tools.
