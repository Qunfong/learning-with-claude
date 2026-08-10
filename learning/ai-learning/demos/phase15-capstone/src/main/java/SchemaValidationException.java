import java.util.List;

/**
 * Copied in from {@code phase11-resilient-pipeline}: a malformed handoff is a
 * hard failure carrying every violation found, never a silently coerced
 * default.
 */
public class SchemaValidationException extends RuntimeException {

    private final List<String> violations;

    public SchemaValidationException(List<String> violations) {
        super("handoff failed schema validation: " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
