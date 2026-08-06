import java.util.Arrays;
import java.util.Random;

/**
 * Fase 0 — Logits, temperature, top-k, top-p en sampling.
 *
 * Pure Java, geen netwerk nodig (in tegenstelling tot OllamaDemo). Werkt met
 * een vast toy-vocabulaire van 8 kandidaat-tokens en handgekozen logits, zodat
 * elk effect apart en reproduceerbaar te zien is:
 *
 *   - logits      : ruwe, ongenormaliseerde scores die het model per token geeft
 *   - softmax     : logits -> kansen (sommeren tot 1.0)
 *   - temperature : schaalt de logits VOOR de softmax -> scherpt of vlakt de kansverdeling af
 *   - top-k       : houdt altijd de k tokens met hoogste kans, ongeacht hun kansmassa
 *   - top-p       : houdt zoveel tokens als nodig om samen p kansmassa te bereiken (adaptief)
 *   - sampling    : trekt een token volgens de (gefilterde) kansverdeling i.p.v. altijd de hoogste te pakken
 *
 * Draai met:  mvn -q compile exec:java -Dexec.mainClass=SamplingDemo
 */
public class SamplingDemo {

    // softmax met temperature: exp(logit / T) genormaliseerd. T=1.0 = standaard softmax.
    static double[] softmax(double[] logits, double temperature) {
        double[] scaled = new double[logits.length];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            scaled[i] = logits[i] / temperature;
            max = Math.max(max, scaled[i]);
        }
        double sum = 0;
        double[] probs = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(scaled[i] - max); // -max voor numerieke stabiliteit
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) probs[i] /= sum;
        return probs;
    }

    // indices gesorteerd op probs, hoog naar laag
    static Integer[] rankByProb(double[] probs) {
        Integer[] idx = new Integer[probs.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(probs[b], probs[a]));
        return idx;
    }

    static void printDistribution(String label, String[] tokens, double[] probs) {
        Integer[] order = rankByProb(probs);
        System.out.println(label);
        for (int i : order) {
            System.out.printf("  %-12s %6.2f%%%n", tokens[i], probs[i] * 100);
        }
    }

    // top-k: houd de k hoogste, zet de rest op 0, renormaliseer over wat overblijft
    static double[] topKFilter(double[] probs, int k) {
        Integer[] order = rankByProb(probs);
        double[] out = new double[probs.length];
        double kept = 0;
        for (int i = 0; i < k; i++) {
            out[order[i]] = probs[order[i]];
            kept += probs[order[i]];
        }
        for (int i = 0; i < out.length; i++) out[i] /= kept;
        return out;
    }

    // top-p (nucleus): houd tokens hoog->laag tot cumulatieve kans >= p, rest op 0, renormaliseer
    static double[] topPFilter(double[] probs, double p) {
        Integer[] order = rankByProb(probs);
        double[] out = new double[probs.length];
        double cumulative = 0;
        int count = 0;
        for (int i : order) {
            if (cumulative >= p) break;
            out[i] = probs[i];
            cumulative += probs[i];
            count++;
        }
        double kept = cumulative;
        for (int i = 0; i < out.length; i++) out[i] /= kept;
        System.out.printf("  (top-p hield %d/%d tokens aan om %.0f%% massa te bereiken)%n", count, probs.length, p * 100);
        return out;
    }

    // trek 1 token volgens de kansverdeling (multinomial sampling)
    static int sample(double[] probs, Random rnd) {
        double r = rnd.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (r < cumulative) return i;
        }
        return probs.length - 1;
    }

    static int argmax(double[] probs) {
        int best = 0;
        for (int i = 1; i < probs.length; i++) if (probs[i] > probs[best]) best = i;
        return best;
    }

    public static void main(String[] args) {
        String[] tokens = {"diep", "groot", "mooi", "koud", "zout", "gevaarlijk", "leeg", "blauw"};
        double[] logitsA = {4.0, 2.5, 2.3, 1.0, 0.8, 0.5, -1.0, -2.0};

        System.out.println("=== 1. Logits -> kansen bij verschillende temperature ===");
        System.out.println("(zelfde logits " + Arrays.toString(logitsA) + ", alleen T verandert)\n");
        for (double temp : new double[]{0.1, 1.0, 2.0}) {
            printDistribution(String.format("-- temperature %.1f --", temp), tokens, softmax(logitsA, temp));
            System.out.println();
        }
        System.out.println("Waarom: T<1 deelt logits door <1 -> verschillen worden UITVERGROOT -> bijna alle massa naar 'diep' (bijna-greedy).");
        System.out.println("        T>1 deelt logits door >1 -> verschillen worden AFGEVLAKT -> kansen liggen dichter bij elkaar (meer variatie mogelijk).\n");

        System.out.println("=== 2. Top-k (k=3) op temperature=1.0 verdeling ===\n");
        double[] baseProbs = softmax(logitsA, 1.0);
        double[] topK = topKFilter(baseProbs, 3);
        printDistribution("-- na top-k filter --", tokens, topK);
        System.out.println("Waarom: top-k pakt ALTIJD precies 3 tokens, ongeacht of de 3e kandidaat nog relevant is.\n");

        System.out.println("=== 3. Top-p (nucleus, p=0.8) — adaptief, in tegenstelling tot top-k ===\n");
        String[] tokensB = {"a", "b", "c", "d", "e", "f", "g", "h"};

        System.out.println("-- Piekende verdeling (1 dominant antwoord) --");
        double[] logitsPeaked = {6.0, 1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4};
        double[] probsPeaked = softmax(logitsPeaked, 1.0);
        printDistribution("kansen:", tokensB, probsPeaked);
        topPFilter(probsPeaked, 0.8);
        System.out.println();

        System.out.println("-- Vlakke verdeling (meerdere gelijkwaardige antwoorden) --");
        double[] logitsFlat = {1.2, 1.1, 1.05, 1.0, 0.95, 0.9, 0.85, 0.8};
        double[] probsFlat = softmax(logitsFlat, 1.0);
        printDistribution("kansen:", tokensB, probsFlat);
        topPFilter(probsFlat, 0.8);
        System.out.println();
        System.out.println("Waarom: bij een piekende verdeling is 1 token al >=80% massa -> top-p stopt vroeg.");
        System.out.println("        Bij een vlakke verdeling zijn er veel tokens nodig om aan 80% te komen -> top-p neemt er meer mee.");
        System.out.println("        Top-k=3 zou in BEIDE gevallen blind 3 tokens pakken, ook als 1 genoeg was of 3 te weinig is.\n");

        System.out.println("=== 4. Sampling: greedy (argmax) vs trekken uit de verdeling ===\n");
        System.out.printf("Greedy (T=0, altijd hoogste kans): kiest altijd '%s' -- 100%% van de tijd, elke run.%n%n",
                tokens[argmax(baseProbs)]);

        int n = 10_000;
        Random rnd = new Random(42); // vaste seed: reproduceerbaar in dit voorbeeld
        int[] counts = new int[tokens.length];
        for (int i = 0; i < n; i++) counts[sample(baseProbs, rnd)]++;

        System.out.printf("Sampling (T=1.0), %,d trekkingen -- empirisch vs theoretisch:%n", n);
        Integer[] order = rankByProb(baseProbs);
        for (int i : order) {
            System.out.printf("  %-12s empirisch %6.2f%%   theoretisch %6.2f%%%n",
                    tokens[i], 100.0 * counts[i] / n, baseProbs[i] * 100);
        }
        System.out.println("\nWaarom: sampling is een kansdeling, geen keuze. Over genoeg trekkingen convergeert de");
        System.out.println("empirische frequentie naar de theoretische kans -- dat is precies waarom output bij T>0 varieert per call.");
    }
}
