import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightsDemoTest {

    @Test
    void reluKeepsPositiveValues() {
        assertEquals(3.5, WeightsDemo.relu(3.5));
    }

    @Test
    void reluZeroesOutNegativeValues() {
        assertEquals(0.0, WeightsDemo.relu(-2.0));
    }

    @Test
    void reluOfZeroIsZero() {
        assertEquals(0.0, WeightsDemo.relu(0.0));
    }

    @Test
    void neuronSumsWeightedInputsPlusBiasThenActivates() {
        double[] inputs = {1.0, 2.0, 3.0};
        double[] weights = {0.5, 0.0, -0.2};
        // 1.0*0.5 + 2.0*0.0 + 3.0*-0.2 + bias 0.0 = -0.1 -> relu -> 0.0
        assertEquals(0.0, WeightsDemo.neuron(inputs, weights, 0.0));
    }

    @Test
    void neuronAppliesBiasBeforeActivation() {
        double[] inputs = {1.0, 1.0};
        double[] weights = {1.0, 1.0};
        // 1+1 + bias 5 = 7
        assertEquals(7.0, WeightsDemo.neuron(inputs, weights, 5.0));
    }
}
