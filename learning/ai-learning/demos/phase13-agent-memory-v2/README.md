# Phase 13 — Demo: ANN Index, Hybrid Search & Graph Memory (Gap 5)

Goal: close Gap 5 from `learning/ai-learning-gap-review/NOTES.md` ("Memory:
no vector database, no hybrid search/re-ranking, no graph memory"). Two
concrete debts drove this phase. First, `phase2-prompting-rag`'s
`VectorStore` is explicitly a brute-force `List<EmbeddedChunk>` with a full
linear cosine scan — fine for that phase's chunking-strategy lesson, but the
user had never touched an actual ANN index (HNSW/IVF), hybrid BM25+vector
search, or cross-encoder re-ranking. Second, `phase4-agents/MemoryStore`'s
`recall()` is called out in its own README as unranked and unbounded — "geeft
gewoon alles terug -- geen ranking/embeddings" — an "onbegrensde long-term
memory" the README itself flags as a real risk without fixing it. This phase
builds the fixes: a hand-rolled HNSW-lite ANN index benchmarked against the
old brute-force scan, a BM25+vector hybrid search with reciprocal-rank fusion
and cross-encoder-style re-ranking, a bounded/decayed/ranked `MemoryStoreV2`
next to a byte-for-byte copy of the old unranked store for an explicit
before/after comparison, and a small Graph RAG memory (entity/relation
triples) for the "what depends on X" questions vector similarity answers
poorly.

**This models the mechanics behind a production RAG memory stack — it is
NOT Pinecone/Weaviate/Qdrant, NOT a real HNSW library, NOT a trained
cross-encoder model, and NOT Neo4j.** Specifics, since "models the mechanics"
can hide a lot of sins:
- `AnnIndex` is a *real* layered-graph HNSW-lite (randomized insert levels,
  greedy descent through sparse upper layers, bounded beam search at layer
  0 — the actual Malkov & Yashunin algorithm shape) — but it has no
  deletion, no disk persistence, no SIMD/vectorized distance ops, and no
  concurrency. A real HNSW library (hnswlib, Lucene's HNSW codec, FAISS)
  adds all four plus parameter defaults tuned against real recall/latency
  benchmarks, not the `M=16`/`efConstruction=100` guesses used here.
- `Embeddings` is a deterministic hashed bag-of-words + character-trigram
  vector, not a trained embedding model. It has zero semantic understanding
  beyond surface lexical/sub-word overlap — it exists so this module's
  benchmarks run offline and reproducibly (`mvn test` must not depend on a
  live Ollama server), unlike `phase2-prompting-rag`'s `RagDemo`, which calls
  Ollama's real `/api/embed` for every chunk.
- `CrossEncoderRerank` is a hand-rolled weighted blend (token Jaccard +
  exact-phrase bonus + a richer hashed cosine) shaped like a cross-encoder
  (it scores the query/document *pair* directly instead of comparing two
  independent vectors) but it is not an actual transformer jointly attending
  over both texts — no trained model, no attention, no fine-tuning.
- `GraphMemory` is a plain in-memory `Map<String, List<Triple>>` adjacency
  list — no Cypher, no query planner, one relation direction stored per
  triple, and `whatDependsOn` does a full linear scan of every stored triple
  rather than an indexed reverse lookup. It is not Neo4j and would not
  survive past a few thousand triples the way a real graph database would.

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — no imports from
`phase2-prompting-rag/` or `phase4-agents/`. `VectorStore` and
`LegacyMemoryStore` are copied in from those phases and adapted (see
"Deviations" below); everything else is new to this phase.

## Architecture

| Class | Role |
|---|---|
| `Embeddings` | deterministic, offline hashed bag-of-words + trigram "embedding" (`float[]`) plus L2-normalize and cosine similarity — the shared vector primitive every other class in this module builds on |
| `VectorStore` | copied in from `phase2-prompting-rag/RagDemo`'s inner `VectorStore`: brute-force `List<EmbeddedChunk>`, full linear cosine scan per query. The **baseline** `AnnIndex` is benchmarked against |
| `AnnIndex` | hand-rolled HNSW-lite: layered graph, randomized insert levels, greedy descent through upper layers, bounded beam search (`ef`) at layer 0. Exposes `distanceComparisons` per search so `AnnBenchmark` can measure — not just claim — that it visits far fewer vectors than a full scan |
| `Bm25` | hand-rolled Robertson/Sparck-Jones BM25 keyword scorer over a fixed corpus — the "keyword" half of hybrid search, catches exact identifier matches a coarse hashed embedding can dilute |
| `HybridSearch` | BM25 rank + vector-cosine rank, fused via Reciprocal Rank Fusion (Cormack, Clarke & Buettcher 2009), then `CrossEncoderRerank`-scores only the fused shortlist — the retrieve-then-rerank two-stage pattern real RAG systems use |
| `CrossEncoderRerank` | hand-rolled (query, document)-pair scorer (Jaccard overlap + exact-phrase bonus + a higher-dimension cosine) standing in for a real cross-encoder model, deliberately pricier per call than `Embeddings.cosine` so it's only run on `HybridSearch`'s small shortlist |
| `GraphMemory` | in-memory entity/relation triple store (`Project USES Postgres`) with `whatDependsOn(target)` reverse-dependency lookup — the Graph RAG answer to "what depends on X," a question vector similarity is structurally bad at |
| `TripleExtractor` | interface for turning a plain-language fact into a `GraphMemory.Triple` |
| `RuleBasedTripleExtractor` | deterministic regex implementation ("X depends on Y" / "X requires Y" / "X uses Y") — offline, no model needed |
| `OllamaTripleExtractor` | the "one LLM call" implementation: sends all facts to a local Ollama `/api/chat` and parses a JSON triple array back — needs a live `ollama serve` |
| `LegacyMemoryStore` | verbatim copy-in of `phase4-agents/MemoryStore`'s original `remember`/`recall`/`load` — unranked, unbounded, `query` parameter accepted but unused. Kept only as the "before" half of a before/after comparison |
| `MemoryStoreV2` | the fix: cosine-ranked, hard-bounded at `MAX_RESULTS=10`, and time-decayed (`DECAY_RATE=0.03`) so a stale-but-lexically-similar fact can't dominate a ranking forever; recalling a fact resets its decay clock |
| `SyntheticData` | clustered random unit vectors (topic centroids + gaussian noise) standing in for embedded text chunks, so `AnnBenchmark` doesn't need real text or a live embedding model |
| `AnnBenchmark` | runnable `main` — the only wired-up entry point in this module (see "Deviations") |

## Running it

Run from **this** directory (`demos/phase13-agent-memory-v2/`).

```bash
mvn -q compile exec:java
```

No live Ollama server or network access is needed for this. `AnnBenchmark`
only exercises `VectorStore`, `AnnIndex`, `SyntheticData`, and `Embeddings`
(the hashed, offline embedding), all fully self-contained. The one class
that *does* need a live model — `OllamaTripleExtractor` (`ollama serve` +
`llama3.2:3b` pulled) — is never called by `AnnBenchmark` or by anything
else runnable in this module; it exists as a complete, compilable
implementation of `TripleExtractor` that nothing currently invokes.
`RuleBasedTripleExtractor` is the deterministic, always-available
implementation, but it likewise isn't wired into a demo main here — see
below.

## What the demo actually demonstrates

`AnnBenchmark` runs `VectorStore` (brute force) and `AnnIndex` (HNSW-lite)
side by side at N = 50 / 500 / 2000 synthetic clustered vectors, 30 queries
per run, `k=5`. Real captured output from `mvn -q compile exec:java`:

```
==============================================================================
ANN benchmark: hand-rolled HNSW-lite (AnnIndex) vs. brute-force (VectorStore)
dims=32  k=5  queries/run=30  M=16  efConstruction=100  efSearch=50
==============================================================================
N            brute ms/q       ann ms/q      speedup   recall@k ann comparisons
50               0.1547         0.2132        0.73x      1.000           51.0
500              0.1519         0.1448        1.05x      1.000          315.9
2000             0.6326         0.2041        3.10x      1.000          459.5
```

The number that actually matters here is the last column, not the
millisecond timings: brute force does exactly N distance comparisons per
query by construction (50, 500, 2000 — it scans everything), while
`AnnIndex`'s comparisons grow far sub-linearly (51 → 316 → 460) as N goes
from 50 to 2000 — a 40x growth in data costs the ANN index roughly 9x more
comparisons, not 40x. That sub-linear growth in *comparisons* is the actual
mechanism HNSW is built on; wall-clock speedup only shows up once N is large
enough to swamp JVM/JIT warm-up noise (note N=50 is actually *slower* than
brute force — graph-traversal overhead dominates at tiny N). `recall@k`
stayed a perfect 1.000 at every N in this run — expected, since the queries
are deterministic subsets of the indexed points themselves (guarantees a
well-defined true nearest neighbor exists), so this benchmark demonstrates
comparison-count scaling, not a recall/speed tradeoff under approximate
conditions.

`HybridSearch`, `CrossEncoderRerank`, `GraphMemory`, `MemoryStoreV2`, and the
`TripleExtractor` implementations are complete, independently testable
classes implementing the rest of Gap 5's scope (hybrid BM25+vector search
with RRF fusion and rerank, ranked/bounded/decayed memory, graph-based
relationship queries) — but none of them are exercised by `AnnBenchmark` or
by any other runnable entry point in this module. Reading them directly is
the way to see what they do; see "Deviations" below for why they're not
wired into a demo.

## Deviations from a hypothetical "fully wired" version

- **Only `AnnBenchmark` is a runnable demo.** `HybridSearch`,
  `GraphMemory`, `MemoryStoreV2`/`LegacyMemoryStore`, and both
  `TripleExtractor` implementations compile clean and are complete in
  isolation, but nothing in this module calls
  `HybridSearch.hybridSearchWithRerank(...)`,
  `GraphMemory.whatDependsOn(...)`, or `MemoryStoreV2.recall(...)` from a
  `main` method. There is no `HybridSearchDemo` or `GraphMemoryDemo`
  entry point, even though several classes' javadoc (`OllamaTripleExtractor`,
  `TripleExtractor`) references one (`GraphMemoryDemo`'s "live run") as if
  it exists. It doesn't, in this module as delivered.
- **No test suite.** `src/test/java` exists as an empty directory and the
  `pom.xml` declares `junit-jupiter`, but there are zero test files. Several
  javadoc comments reference tests by name that were evidently intended —
  `GraphMemoryTest`'s "negative test," `MemoryStoreV2Test`'s before/after
  comparison against `LegacyMemoryStore` — but none of them exist in the
  delivered source. Nothing in this module is currently covered by an
  automated test; the only verification is `AnnBenchmark`'s printed output
  and a clean `mvn -o compile`.
- **`AnnIndex` parameters (`M=16`, `efConstruction=100`, `efSearch=50`) are
  reasonable guesses, not benchmark-tuned values** the way a real ANN
  library's defaults are derived from published recall/QPS curves.
- **`Embeddings`' hashed bag-of-words + trigram scheme is not a claim that
  hashed n-grams rival a trained embedding model.** It ranks text by surface
  lexical/sub-word overlap well enough to make this module's benchmarks and
  rerank scoring behave sensibly, with zero external dependencies and fully
  deterministic output — that trade only makes sense because this module's
  job is exercising ANN/rerank *mechanics*, not embedding quality.
- **`GraphMemory` stores one relation direction per triple with no reverse
  index** — `whatDependsOn` is a linear scan over every stored triple. Fine
  at demo scale (dozens to low hundreds of triples), not a design that
  scales the way a real graph database's indexed traversal does.
