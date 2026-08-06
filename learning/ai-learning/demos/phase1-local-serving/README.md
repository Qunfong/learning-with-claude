# Fase 1 — Demo: Local vs Hosted achter één interface

Doel: laten zien dat "lokaal of hosted model" een implementatiedetail is.
De app praat alleen tegen `ModelClient.complete(prompt)`; wisselen van backend
is een andere impl injecteren — geen refactor. Dit is het **strategy pattern**
toegepast op model-keuze.

## Wat het doet
Draait dezelfde prompt door elke beschikbare backend en logt per call:
- **latency** (ms, ruwe muur-tijd = TTFT + generatie samen)
- **tokengebruik** (in/uit)

Zo zie je de tradeoff live: lokaal = privé + gratis per call maar begrensd door
je hardware; hosted = sterker maar per-token en met netwerk-hop.

## Runnen
Maven-project (JDK 17+). Jackson wordt automatisch gedownload:
```
mvn -q compile exec:java
```

### Backend 1 — Ollama (lokaal), altijd aan
Vereist Ollama lokaal:
```
ollama pull llama3
ollama serve
```

### Backend 2 — Claude (hosted), optioneel
Zet je API-key als env var; zonder key wordt deze backend overgeslagen.

PowerShell:
```
$env:ANTHROPIC_API_KEY = "sk-ant-..."
mvn -q compile exec:java
```
bash:
```
export ANTHROPIC_API_KEY="sk-ant-..."
mvn -q compile exec:java
```

> **Let op:** de API-key staat NIET in de code en hoort er niet in. Alleen via
> env var. Commit nooit een key.

## Experimenteer
- Verander `prompt` in `main` en vergelijk latency/tokens tussen backends.
- Wissel het lokale model (`new OllamaClient("llama3")` → een ander gepulld model)
  en zie het effect op snelheid en kwaliteit.
- Merk op: de `for`-loop over `ModelClient` bevat GEEN if/else per backend —
  dat is precies het punt van de interface.

## Bewuste vereenvoudigingen (voor productie anders doen)
- **JSON via Jackson `ObjectMapper`** — `readTree`/`JsonNode` voor parsen,
  `createObjectNode` voor bodies. `path(...)` i.p.v. `get(...)` zodat een
  ontbrekend veld een lege node geeft i.p.v. NPE.
- **Geen streaming** — `stream=false` bij Ollama, geen SSE bij Claude. Echte
  TTFT meet je alleen met streaming; hier is latency de totale call-tijd.
- **Geen retries/backoff** — hosted API's hebben rate limits; een echte client
  heeft retry-logica nodig.

## Volgende demo
`phase2-rag/` — context engineering + RAG over je eigen Java-codebase.
