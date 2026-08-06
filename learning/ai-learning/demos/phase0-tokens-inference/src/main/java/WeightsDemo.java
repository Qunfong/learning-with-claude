/**
 * Fase 0 — Wat DOET een parameter (weight)?
 *
 * Eén neuron doet één simpele som: output = activatie(Σ inputᵢ·wᵢ + bias).
 * Een model met "7B parameters" = 7 miljard van deze w's en biases samen.
 * Er is geen kennis als tekst opgeslagen — kennis = de combinatie van getallen.
 *
 * Deze demo bouwt één neuron met 3 inputs, laat zien hoe de output verandert
 * als je de weights bijstelt, en doet daarna een piepklein stukje "training":
 * de weights langs stapjes bijstellen tot de output een doelwaarde nadert.
 * Dat laatste IS in essentie wat trainen doet — alleen dan met miljarden weights.
 *
 * Pure Java, geen dependencies.
 * Draai met:  mvn -q compile exec:java -Dexec.mainClass=WeightsDemo
 */
public class WeightsDemo {

    // activatie: ReLU (negatuf -> 0, anders zichzelf). Simpelste non-lineariteit.
    static double relu(double x) {
        return Math.max(0, x);
    }

    // één neuron: gewogen som van inputs + bias, door de activatie
    static double neuron(double[] inputs, double[] weights, double bias) {
        double sum = bias;
        for (int i = 0; i < inputs.length; i++) {
            sum += inputs[i] * weights[i];
        }
        return relu(sum);
    }

    public static void main(String[] args) {
        double[] inputs = {1.0, 2.0, 3.0};

        System.out.println("== Één neuron, verander de weights, zie de output ==");
        double[] w1 = {0.1, 0.1, 0.1};
        double[] w2 = {0.5, 0.0, -0.2};
        double[] w3 = {2.0, 1.0, 0.5};
        System.out.printf("inputs %s%n", java.util.Arrays.toString(inputs));
        System.out.printf("weights %s + bias 0.0 -> output %.2f%n", java.util.Arrays.toString(w1), neuron(inputs, w1, 0.0));
        System.out.printf("weights %s + bias 0.0 -> output %.2f%n", java.util.Arrays.toString(w2), neuron(inputs, w2, 0.0));
        System.out.printf("weights %s + bias 0.0 -> output %.2f%n", java.util.Arrays.toString(w3), neuron(inputs, w3, 0.0));

        System.out.println("\n== Mini-training: stel weights bij tot output ~= doel ==");
        // start met willekeurige weights, duw ze richting een doeloutput.
        // dit is gradient descent in het klein: fout meten -> weights corrigeren.
        double target = 10.0;
        double[] w = {0.0, 0.0, 0.0};
        double bias = 0.0;
        double lr = 0.01; // learning rate: hoe groot elke stap is

        for (int step = 1; step <= 200; step++) {
            double out = neuron(inputs, w, bias);
            double error = target - out;            // hoe ver zitten we ernaast?
            // corrigeer elke weight evenredig met zijn input (de "gradient")
            for (int i = 0; i < w.length; i++) {
                w[i] += lr * error * inputs[i];
            }
            bias += lr * error;

            if (step % 40 == 0 || step == 1) {
                System.out.printf("stap %3d: output %.3f (fout %.3f)  weights %s%n",
                        step, out, error, fmt(w));
            }
        }
        System.out.printf("%neindresultaat: output %.3f, doel %.1f%n", neuron(inputs, w, bias), target);
        System.out.println("De weights zijn 'geleerd' -- puur door herhaald bijstellen op de fout.");
    }

    static String fmt(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.3f", a[i]));
        }
        return sb.append("]").toString();
    }
}
