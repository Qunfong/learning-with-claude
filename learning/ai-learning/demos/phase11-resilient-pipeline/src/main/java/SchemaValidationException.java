import java.util.List;

/**
 * Thrown by {@link HandoffValidator} when a handoff payload (a JSON blob
 * meant to become a {@link TaskResult}) fails {@link HandoffSchema}
 * validation -- missing required field, wrong type, invalid enum value, or
 * simply not parseable as JSON at all. Carries every violation found (not
 * just the first) so the caller can log/escalate with full detail, and is
 * NEVER silently swallowed into a coerced/default value -- see
 * {@link HandoffValidator#parseAndValidate} javadoc for why.
 */
class SchemaValidationException extends RuntimeException {

    private final List<String> violations;

    SchemaValidationException(List<String> violations) {
        super("handoff rejected -- schema violations: " + violations);
        this.violations = violations;
    }

    List<String> violations() {
        return violations;
    }
}
