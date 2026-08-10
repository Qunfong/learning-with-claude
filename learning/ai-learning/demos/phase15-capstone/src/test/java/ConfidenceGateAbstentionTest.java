import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the phase-11 lesson this capstone reuses: the schema gate and the
 * confidence gate are two DIFFERENT mechanisms answering two different
 * questions, and neither one does the other's job.
 */
class ConfidenceGateAbstentionTest {

    @Test
    void malformedConfidence_isRejectedByTheSchemaGate_notCoerced() {
        String malformed = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":\"fairly confident\"}";

        SchemaValidationException ex = assertThrows(SchemaValidationException.class,
                () -> HandoffValidator.parseAndValidate(malformed));
        assertTrue(ex.violations().stream().anyMatch(v -> v.contains("confidence")),
                "expected a violation naming 'confidence', got: " + ex.violations());
    }

    @Test
    void invalidStateEnumValue_isRejected() {
        String badState = "{\"taskId\":\"t1\",\"state\":\"MAYBE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":0.9}";

        assertThrows(SchemaValidationException.class, () -> HandoffValidator.parseAndValidate(badState));
    }

    @Test
    void confidenceOutsideUnitRange_isRejected() {
        String outOfRange = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":1.4}";

        assertThrows(SchemaValidationException.class, () -> HandoffValidator.parseAndValidate(outOfRange));
    }

    @Test
    void lowConfidenceResult_isSchemaValidButFailsTheConfidenceGate() {
        String lowConfidence = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"unsure\",\"confidence\":0.35}";

        TaskResult result = HandoffValidator.parseAndValidate(lowConfidence);

        assertEquals(0.35, result.confidence(), "schema gate must accept it: it is well-formed");
        assertFalse(CapstoneDemo.passesConfidenceGate(result),
                "a 0.35-confidence result must not pass the abstention gate");
    }

    @Test
    void gateBoundaryIsInclusive() {
        TaskResult atThreshold = new TaskResult("t1", "DONE", List.of(), List.of(), "ok",
                CapstoneEval.CONFIDENCE_THRESHOLD);
        TaskResult justBelow = new TaskResult("t1", "DONE", List.of(), List.of(), "ok",
                CapstoneEval.CONFIDENCE_THRESHOLD - 0.0001);

        assertTrue(CapstoneDemo.passesConfidenceGate(atThreshold), "threshold itself must pass");
        assertFalse(CapstoneDemo.passesConfidenceGate(justBelow));
    }
}
