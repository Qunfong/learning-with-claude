import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two DIFFERENT gates a handoff must pass, and confirms they are
 * genuinely separate mechanisms rather than one doing the other's job:
 *   1. {@link HandoffValidator} -- is this even a well-formed TaskResult?
 *      (schema shape, types, enum values). A malformed payload never
 *      silently coerces into something valid.
 *   2. {@link ResilientPipeline#passesConfidenceGate} -- is this
 *      well-formed result confident ENOUGH to hand to the next agent? A
 *      schema-valid but low-confidence TaskResult still parses fine; it's
 *      the pipeline, not the validator, that decides to abstain on it.
 */
class HandoffValidatorAbstentionTest {

    @Test
    void rejectsMalformedTaskResult_doesNotSilentlyCoerce() {
        String malformed = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":\"very confident\"}";

        SchemaValidationException ex = assertThrows(SchemaValidationException.class,
                () -> HandoffValidator.parseAndValidate(malformed));
        assertTrue(ex.violations().stream().anyMatch(v -> v.contains("confidence")),
                "expected a violation naming the malformed 'confidence' field, got: " + ex.violations());
    }

    @Test
    void rejectsInvalidStateEnumValue() {
        String badState = "{\"taskId\":\"t1\",\"state\":\"MAYBE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":0.5}";

        assertThrows(SchemaValidationException.class, () -> HandoffValidator.parseAndValidate(badState));
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        String outOfRange = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"ok\",\"confidence\":1.5}";

        assertThrows(SchemaValidationException.class, () -> HandoffValidator.parseAndValidate(outOfRange));
    }

    @Test
    void schemaValidButLowConfidence_parsesFineButFailsThePipelineGate() {
        String lowConfidence = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"unsure\",\"confidence\":0.2}";

        TaskResult result = HandoffValidator.parseAndValidate(lowConfidence);
        assertEquals(0.2, result.confidence());
        assertFalse(ResilientPipeline.passesConfidenceGate("TestAgent", result),
                "a 0.2-confidence result must not pass the pipeline's abstention gate");
    }

    @Test
    void schemaValidAndHighConfidence_passesThePipelineGate() {
        String highConfidence = "{\"taskId\":\"t1\",\"state\":\"DONE\",\"artifacts\":[],\"issues\":[],"
                + "\"message\":\"confident\",\"confidence\":0.95}";

        TaskResult result = HandoffValidator.parseAndValidate(highConfidence);
        assertTrue(ResilientPipeline.passesConfidenceGate("TestAgent", result));
    }
}
