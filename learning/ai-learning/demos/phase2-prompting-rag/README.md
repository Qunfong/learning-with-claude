# Fase 2 — Demo: Context engineering & RAG

Doel: laten zien dat **prompt-structuur** en **context-kwaliteit** het antwoord
net zo hard bepalen als het model zelf. Twee demo's, zelfde model, zelfde
vraag — alleen de opbouw van wat je meestuurt verandert.

## Demo 1 — `PromptStructureDemo`
Zelfde vraag ("Wat is een token in een LLM?"), 3x verstuurd:

- **A) Bare prompt** — geen system prompt, geen voorbeelden
- **B) System prompt** — rol + format-instructie ("antwoord in precies 2 zinnen")
- **C) Few-shot** — 2 voorbeelden vóór de echte vraag, zodat het model het
  antwoordpatroon uit de voorbeelden overneemt

```bash
mvn -q compile exec:java "-Dexec.mainClass=PromptStructureDemo"
```

## Demo 2 — `RagDemo`
Kernvraag: *"Wat is de connectTimeout in de OllamaClient en waarom staat
stream op false?"* — specifiek genoeg dat het model zonder context faalt of
hallucineert. Indexeert alle `.java`-bestanden in de repo (behalve zichzelf)
en vergelijkt 3 scenario's:

1. **Zonder RAG** — model weet het niet / hallucineert
2. **Kleine chunks** (300 tekens, vaste grootte) — knipt dwars door methodes
3. **Semantische chunks** (split op lege regels) — methode-blokken blijven
   intact → completer, accurater antwoord

In-memory `VectorStore`: `List<EmbeddedChunk>` + cosine similarity. Embedding
via Ollama `/api/embed` met `llama3.2:3b`.

```bash
mvn -q compile exec:java "-Dexec.mainClass=RagDemo"
```

> **Let op:** indexeren kost tijd (~2-5s per chunk, elke chunk is een aparte
> embed-call). Voortgang wordt tijdens indexering geprint.

## Runnen
Maven-project (JDK 17+). Vereist Ollama lokaal:
```
ollama pull llama3.2:3b
ollama serve
```
**PowerShell:** quote de `-D`-flag, anders splitst PowerShell 'm:
`mvn -q compile exec:java "-Dexec.mainClass=RagDemo"`.

## Key Learnings
- **Chunk-strategie bepaalt wat er in de context belandt.** Dezelfde bron-code,
  anders opgeknipt, geeft een ander antwoord — het model zelf verandert niet.
- **Te klein = context halverwege afgeknipt.** Vaste-grootte chunking kent geen
  code-structuur; een chunk kan letterlijk midden in een methode-signature
  eindigen.
- **Semantisch chunken (op lege regels) houdt methode-grenzen intact** →
  betere retrieval, want de opgehaalde chunk is een complete, samenhangende
  eenheid in plaats van een willekeurig tekstfragment.
- **Prompt-structuur (Demo 1) is net zo'n hefboom als context (Demo 2).**
  Zelfde model, zelfde vraag, alleen system-prompt/few-shot toegevoegd →
  significant andere stijl, lengte en precisie.

## Experimenteer
- Verander `charSize` in `chunkBySize` en zie het effect op retrieval-kwaliteit.
- Stel een vraag die over meerdere bestanden gaat tegelijk — zie hoe `topK`
  de balans tussen recall en context-lengte beïnvloedt.
- Verander de few-shot voorbeelden in `PromptStructureDemo` en zie hoe sterk
  het model het patroon overneemt (of juist niet).

## Bewuste vereenvoudigingen (voor productie anders doen)
- **`VectorStore` is een `List`** — productie gebruikt pgvector/Qdrant/Weaviate
  voor scale + persistentie; hier is het punt het RETRIEVAL-mechanisme, niet
  de opslag-engine.
- **Geen reranking** — top-K op cosine similarity alleen; een productie-RAG
  voegt vaak een rerank-stap toe na de initiële retrieval.
- **Geen retries/backoff, geen streaming** — zelfde vereenvoudiging als in de
  eerdere fases.

## Volgende demo
`phase3-tool-calling/` — tool/function calling: het model roept geen functie
aan, het *vraagt erom* via gestructureerde JSON.
