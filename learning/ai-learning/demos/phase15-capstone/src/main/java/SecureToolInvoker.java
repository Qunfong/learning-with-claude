import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Minimal re-implementation of {@code phase9-agent-iam/SecureToolInvoker} -
 * copied in and trimmed, not imported.
 *
 * <p>The credential arrives as a separate Java parameter, never assembled into
 * the {@code JsonNode args} a model produced. Expiry is checked before scope
 * (an expired credential that ALSO lacks the scope is reported as expired -
 * "this credential should not exist any more" is a stronger fact than "it
 * would not have been enough anyway"), the same ordering
 * {@code phase9-agent-iam/GuardrailsTest.authorize_expiryCheckedBeforeScopeCheck}
 * pins.
 *
 * <p>Trimmed vs. phase 9: no {@code AuditLog} (this module's audit trail is
 * {@link RunTrace}, which the eval layer then reads) and no
 * {@code redactCredentialLeak} pass on the result - the prompt-injection
 * defense is phase 9's own lesson and would not change anything in this
 * chain's deterministic tools.
 */
public final class SecureToolInvoker {

    /**
     * @param executed true only if the tool's executor actually ran. The
     *                 invariant {@code !authorized implies !executed} is what
     *                 {@link CapstoneEval} independently re-checks off the trace.
     */
    public record Invocation(String tool, boolean authorized, boolean executed, String result,
                             String denialReason) {
    }

    private SecureToolInvoker() {
    }

    public static Invocation invoke(ScopedCredential credential, Tool tool, JsonNode args, Instant now) {
        if (credential.isExpired(now)) {
            return new Invocation(tool.name(), false, false, null,
                    "credential expired at " + credential.expiresAt());
        }
        if (!credential.hasScope(tool.requiredScope())) {
            return new Invocation(tool.name(), false, false, null,
                    "scope '" + tool.requiredScope() + "' not granted to " + credential.principal());
        }
        String result = tool.executor().apply(args);
        return new Invocation(tool.name(), true, true, result, null);
    }
}
