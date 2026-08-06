# Phase 0 — Demo: Weights & Tokens

Maven project (JDK 17+). Two demos, each with its own main class.

> **PowerShell:** quote the `-D` flag, otherwise PowerShell will split it:
> `mvn -q compile exec:java "-Dexec.mainClass=WeightsDemo"`. In bash/cmd
> you can run it without quotes.

---

## 1. WeightsDemo — what does a parameter do?
Pure math, no network connection required. Shows: `input × weight + bias → activation = output`.
Changing the weights changes the output. It then performs a mini-"training":
adjusting the weights step-by-step based on the error until the output approaches a target (gradient descent).

```bash
mvn -q compile exec:java "-Dexec.mainClass=WeightsDemo"
```

### Example Run Output:
```text
== One neuron, change the weights, see the output ==
inputs [1.0, 2.0, 3.0]
weights [0.1, 0.1, 0.1] + bias 0.0 -> output 0.60
weights [0.5, 0.0, -0.2] + bias 0.0 -> output 0.00
weights [2.0, 1.0, 0.5] + bias 0.0 -> output 5.50

== Mini-training: adjust weights until output ~= target ==
step   1: output 0.000 (error 10.000)  weights [0.100, 0.200, 0.300]
step  40: output 9.982 (error 0.018)  weights [0.666, 1.331, 1.997]
step  80: output 10.000 (error 0.000)  weights [0.667, 1.333, 2.000]
step 120: output 10.000 (error 0.000)  weights [0.667, 1.333, 2.000]
step 160: output 10.000 (error 0.000)  weights [0.667, 1.333, 2.000]
step 200: output 10.000 (error 0.000)  weights [0.667, 1.333, 2.000]

final result: output 10.000, target 10.0
The weights are 'learned' -- purely by repeated correction based on the error.
```

### Key Learnings:
- **100% Deterministic:** Since the starting weights (`[0.0, 0.0, 0.0]`), inputs, targets, and learning rate are fixed, every run of `WeightsDemo` yields **exactly the same results** (Run 1 and Run 2 are identical).
- **What is a parameter (weight)?** An AI model consists of numbers (weights and biases). 
  - *Formula:* `output = activation( (input1 * w1) + (input2 * w2) + (input3 * w3) + bias )`
- **Knowledge = Combination of Numbers:** A model (like `llama3.2:3b`) has billions of these weights. Knowledge lies in the specific combination of these numbers, not in stored text.
- **Activation (ReLU):** The `relu(x)` method (everything below 0 becomes 0) adds a non-linear bend, which is essential for learning complex relationships.
- **What is "Training"?** We start with weights at `0.0`. At each step, we measure the error (target - output) and adjust the weights by a small step (`learning rate = 0.01`). Over time, the error becomes `0.0`. This is gradient descent in miniature.

---

## 2. OllamaDemo — tokens & sampling in practice
Calls a real local model and shows your actual token usage:
`prompt_eval_count` (input tokens) and `eval_count` (output tokens). Runs the same prompt at temperature 0.0 and 1.0 so you can observe the sampling effect.

Requires local Ollama:
```bash
ollama pull llama3.2:3b
ollama serve
mvn -q compile exec:java "-Dexec.mainClass=OllamaDemo"
```

### Comparison of 2 consecutive runs:

````carousel
```text
=== RUN 1 ===
=== temperature 0.0 ===
antwoord : Een verrassend feit over de oceaan is dat er een "grote diepe kloof" in de oceaan bestaat, de Mariana-deep, waar de diepte zo groot is dat er geen licht meer door kan dringen en waarin zelfs de dichtstbijzijnde planeet, de Aarde, niet kan worden waargenomen.
tokens   : in=44  uit=85

=== temperature 1.0 ===
antwoord : Een verrassend feit over de oceaan is dat er ongeveer 95% van de water op aarde niet gemakkelijk te drinken is, omdat het met kookgasen verontreinigd is en een laag concentratie micro-organismen bevat die menselijke immuniteit kunnen oanvallen.
tokens   : in=44  uit=77
```
<!-- slide -->
```text
=== RUN 2 ===
=== temperature 0.0 ===
antwoord : Een verrassend feit over de oceaan is dat er een "grote diepe kloof" in de oceaan bestaat, de Mariana-deep, waar het water zo diep is dat er geen licht meer kan doordringen en waarin zelfs de dichtstbijzijnde planeet, de Aarde, niet kan worden gezien.
tokens   : in=44  uit=83

=== temperature 1.0 ===
antwoord : Een verrassend feit over de oceaan is dat er een underwater-brug in de Grote Oceaan staat die door onderwaterduikers wordt ontdekt, deze brug heet de 'Bridge of Shoals' en ligt op ongeveer 2.000 meter diep onder het oppervlakte van de oceaan.
tokens   : in=44  uit=78
```
````

### Key Learnings:
- **Tokens & Tokenization:** An LLM reads/writes in *tokens* (averaging ~4 characters). The input tokens remain constant (`44`), but the output tokens vary based on the generated response.
- **Influence of Temperature:**
  - **Temperature 0.0 (Nearly fully deterministic):** Strictly chooses the most probable next token. The answers between Run 1 and Run 2 are almost identical (slight variations can occur due to parallel float roundings on CPU/GPU threads).
  - **Temperature 1.0 (Creative / Variable):** The model is free to choose less probable tokens. The answers between Run 1 and Run 2 are completely different (drinking water vs. underwater bridge).
- **Why this happens under the hood:** see `SamplingDemo` below — same softmax/temperature math, just isolated on a toy vocab so the distribution is visible instead of hidden inside the model's 3.2B parameters.

---

## 3. SamplingDemo — logits, temperature, top-k, top-p, sampling
Pure math, no network connection required (unlike OllamaDemo). Uses a fixed toy vocabulary
(8 candidate tokens) with hand-picked logits so each mechanism can be isolated and reproduced exactly.

```bash
mvn -q compile exec:java "-Dexec.mainClass=SamplingDemo"
```

### Example Run Output:
```text
=== 1. Logits -> kansen bij verschillende temperature ===
(zelfde logits [4.0, 2.5, 2.3, 1.0, 0.8, 0.5, -1.0, -2.0], alleen T verandert)

-- temperature 0.1 --
  diep         100.00%
  groot          0.00%
  ...

-- temperature 1.0 --
  diep          65.11%
  groot         14.53%
  mooi          11.90%
  koud           3.24%
  zout           2.65%
  gevaarlijk     1.97%
  leeg           0.44%
  blauw          0.16%

-- temperature 2.0 --
  diep          38.02%
  groot         17.96%
  mooi          16.25%
  koud           8.48%
  zout           7.68%
  gevaarlijk     6.61%
  leeg           3.12%
  blauw          1.89%

=== 2. Top-k (k=3) op temperature=1.0 verdeling ===
-- na top-k filter --
  diep          71.13%
  groot         15.87%
  mooi          12.99%
  (rest -> 0.00%)

=== 3. Top-p (nucleus, p=0.8) — adaptief, in tegenstelling tot top-k ===
-- Piekende verdeling --        (top-p hield 1/8 tokens aan om 80% massa te bereiken)
-- Vlakke verdeling --          (top-p hield 7/8 tokens aan om 80% massa te bereiken)

=== 4. Sampling: greedy (argmax) vs trekken uit de verdeling ===
Greedy (T=0): kiest altijd 'diep' -- 100% van de tijd.

Sampling (T=1.0), 10.000 trekkingen -- empirisch vs theoretisch:
  diep         empirisch  64.83%   theoretisch  65.11%
  groot        empirisch  14.89%   theoretisch  14.53%
  ...
```

### Key Learnings:
- **Logits are raw scores, not probabilities.** `softmax` turns them into a distribution that sums to 1.0. Everything downstream (temperature, top-k, top-p, sampling) operates on that distribution — logits alone can't be compared as "confidence".
- **Temperature scales the logits *before* softmax, not the probabilities after.** `T<1` divides by a number smaller than 1, which stretches the differences between logits apart → the softmax exaggerates the winner (T=0.1 → 100% on one token, near-greedy). `T>1` compresses the differences → the distribution flattens (T=2.0 spreads mass across all 8 tokens). This is why "temperature 0" in APIs is really "very low but not exactly zero" in practice.
- **Top-k is a fixed count, blind to shape.** It always keeps exactly *k* tokens, whether the 3rd-best candidate carries 13% of the mass or 0.01%. Demonstrated by reusing the same distribution: top-k=3 cuts identically regardless of whether that cutoff makes sense.
- **Top-p (nucleus) is adaptive — it reacts to the actual shape of the distribution.** On a peaked distribution (one dominant answer), top-p=0.8 needs only 1 token to hit 80% mass. On a flat distribution (several near-equal answers), it needs 7 of 8 tokens to reach the same 80%. Same `p`, very different token counts — this is the concrete reason top-p is generally preferred over top-k when the model's confidence varies a lot between prompts.
- **Sampling is a weighted draw, not "randomness".** With `temperature=0`/greedy, `argmax` always picks the same token — 100% deterministic. With `temperature=1.0`, drawing 10,000 times converges the *empirical* frequency to the *theoretical* probability (e.g. "diep" ≈ 65% either way) — this is exactly why the same prompt at `T>0` gives different output on every call: it's not noise, it's sampling from a fixed distribution.
- **Maps directly onto `OllamaDemo`'s real output (see section 2):** at `T=0.0` the real model repeats the same Mariana-trench answer — that's the toy `T=0.1` case (mass collapses onto the top token). At `T=1.0` the real model jumps to a completely different answer (dolphins instead of the trench) — that's the toy sampling table: the model didn't pick wrong, it drew one of the lower-probability tokens, exactly as the 10,000-draw table predicts happens some fraction of the time.

## Conscious Simplifications
- **JSON via Jackson** (`ObjectMapper`) — same stack as the subsequent phases.
- **No streaming** (`stream=false`) — one response containing the eval counts.
- **SamplingDemo uses a toy vocab (8 tokens), not real model logits** — real vocabularies have 30k-150k+ tokens, but the softmax/temperature/top-k/top-p math is identical at any scale.

## Next Demo
`phase1-local-serving/` — local vs hosted model behind a single interface.
