# Fase 5 — Demo: Skills & Domain Knowledge

Doel: laten zien dat een "skill" geen framework of magie is — het is een
tekst-bestand dat als system-prompt wordt geïnjecteerd. Zelfde model, zelfde
taak, zelfde temperature; het enige verschil is of `skill.md` wordt meegestuurd.

## Wat het doet
`SkillsDemo` geeft het model dezelfde codeer-opdracht (schrijf een Java-klasse
die een CSV-bestand parsed) twee keer:

- **RUN A — zonder skill**: leeg system-prompt, model schrijft Java op eigen houtje
- **RUN B — met skill**: `skills/java-standards/skill.md` als system-prompt

Daarna laat het model zelf **RUN A en RUN B naast elkaar analyseren** tegen de
10 regels (R1–R10) uit de skill, en print een **token-cost projectie**: hoeveel
extra tokens de skill kost bij realistisch call-volume (30 calls/uur), en wat
dat bij hosted pricing zou kosten.

## Runnen
Maven-project (JDK 17+). Vereist Ollama lokaal met een tool-capable coding model:
```bash
ollama pull qwen2.5-coder:7b
ollama serve
mvn -q compile exec:java
```

De skill wordt gevonden via `SkillsDemo.resolveSkillPath()` — loopt omhoog vanaf
de class-locatie tot `skills/java-standards/skill.md` gevonden wordt, dus werkt
ongeacht working directory (`mvn exec:java`, `java -jar`, IDE run-config).

## Key Learnings
- **Een skill is gewoon een system-prompt.** Geen speciale API, geen aparte
  "skill-injectie"-mechanisme — het is tekst die vóór de user-message in de
  message-lijst gaat. Alles wat een system-prompt kan (rol, stijl, regels,
  voorbeelden) kan een skill.
- **Skill-grootte is een architectuurbeslissing, geen content-beslissing.**
  Elke call met de skill actief betaalt de volledige skill in input-tokens —
  bij hoog call-volume telt dat op. Een skill van 500 tokens is verwaarloosbaar;
  een skill van 3000+ tokens overweeg je op te splitsen in sub-skills per
  taaktype, of zelf te RAG'en (alleen relevante regels ophalen i.p.v. het hele
  bestand).
- **Skills vs RAG vs fine-tuning is een decision matrix, geen hiërarchie.**
  Skills: instant update, stabiele procedurele kennis, goedkoop qua opzet maar
  kost tokens per call. RAG: grote/veranderende corpus, index-kosten maar geen
  system-prompt-overhead. Fine-tuning: stijl/format bakken in de weights zelf,
  duur en traag te itereren. Zie `openspec/specs/phase5-skills/spec.md` voor
  de volledige matrix.

## Experimenteer
- Verander `CODING_TASK` naar een andere opdracht en zie of de skill even
  sterk doorwerkt.
- Maak `skill.md` groter/kleiner en observeer het effect op de token-cost
  projectie (`printCostProjection`).
- Vervang `qwen2.5-coder:7b` door een kleiner model — blijft de skill even
  effectief, of heeft een zwakker model méér sturing nodig?

## Bewuste vereenvoudigingen (voor productie anders doen)
- **Eén system-prompt-injectie, geen sub-skills** — een productie-agent met
  meerdere skills moet kiezen welke skill(s) relevant zijn per taak (routing),
  niet blind alles meesturen.
- **Cost-projectie is een schatting** (1 token ≈ 4 tekens), geen exacte
  tokenizer-count — voor een echte kostenraming gebruik je de tokenizer van
  het specifieke model.
- **Geen retries/backoff, geen streaming** — zelfde vereenvoudiging als in de
  eerdere fases.

## Volgende demo
`phase6-mcp/` — skills vertellen het model HOE iets te doen; MCP geeft het
model TOEGANG om het ook echt te doen.
