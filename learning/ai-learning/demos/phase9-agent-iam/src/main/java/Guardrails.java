import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Copied in from {@code phase4-agents/Guardrails.java} (same
 * {@code maxIterations}/{@code tokenBudget}/{@code loopDetection}/
 * {@code confirmHook} record, unchanged) and extended with the
 * authorization check phase4 never needed: a real IAM boundary, not just a
 * "did the model behave" heuristic. {@link #authorize} is the gate every
 * tool call in this module passes through — see {@link SecureToolInvoker}.
 *
 * <p>{@link #authorize} enforces two independent, code-level facts about
 * the caller's {@link ScopedCredential}, checked BEFORE
 * {@link Tool.ToolExecutor#execute} ever runs:
 * <ol>
 *   <li>not expired ({@code event: expired} on the audit log if it is)</li>
 *   <li>holds {@link Tool#requiredScope()} ({@code event: scope_denied} if not)</li>
 * </ol>
 * Both are deterministic code, not model behavior — exactly the same
 * "guardrail is a system property, not a bet on the model" lesson phase4's
 * README draws about {@code loopDetection}.
 */
record Guardrails(int maxIterations, int tokenBudget, boolean loopDetection, ConfirmHook confirmHook) {

    interface ConfirmHook {
        boolean confirm(String toolName, JsonNode args);
    }

    static Guardrails none() {
        return new Guardrails(0, 0, false, null);
    }

    /** Why {@link #authorize} did or didn't let the call through — explicit, not reconstructed after the fact. */
    enum AuthDecision { OK, EXPIRED, SCOPE_DENIED }

    /**
     * The scope/IAM gate. Returns {@link AuthDecision#OK} only if
     * {@code credential} is unexpired AND carries {@code tool.requiredScope()}.
     * Every outcome — success included — is written to {@code audit} so the
     * trail covers "use" as well as "reject": see {@code AuditLog}'s event
     * vocabulary.
     */
    static AuthDecision authorize(ScopedCredential credential, Tool tool, AuditLog audit) {
        Instant now = Instant.now();

        if (credential.isExpired(now)) {
            audit.write("expired",
                    "credentialId", credential.id(),
                    "principal", credential.principal(),
                    "tool", tool.name(),
                    "requiredScope", tool.requiredScope(),
                    "expiresAt", credential.expiresAt().toString());
            return AuthDecision.EXPIRED;
        }

        if (!credential.hasScope(tool.requiredScope())) {
            audit.write("scope_denied",
                    "credentialId", credential.id(),
                    "principal", credential.principal(),
                    "tool", tool.name(),
                    "requiredScope", tool.requiredScope(),
                    "grantedScopes", String.join(",", credential.grantedScopes()));
            return AuthDecision.SCOPE_DENIED;
        }

        audit.write("use",
                "credentialId", credential.id(),
                "principal", credential.principal(),
                "tool", tool.name(),
                "requiredScope", tool.requiredScope());
        return AuthDecision.OK;
    }

    /**
     * Prompt-injection-as-credential-theft defense: scans a raw tool RESULT
     * string (attacker-controlled — e.g. content read from an external
     * document/API) for the live credential token before that string is
     * allowed anywhere near the LLM's context window. If found, the token
     * substring is replaced with a fixed marker and the attempt is logged
     * (never silently dropped) — using {@link ScopedCredential#tokenFingerprint()},
     * so the audit log itself never contains the secret it just blocked.
     *
     * <p>This is the concrete implementation of AWS Module 8's headline
     * defense: "credentials never enter the model's context window." The
     * broader principle — never put the raw token in a prompt or response in
     * the first place — is enforced structurally by {@link SecureToolInvoker}
     * (the token is never assembled into any {@code Tool} argument or
     * result); this method is the belt-and-braces catch for the case where a
     * malicious tool result tries to smuggle a live token value back in
     * anyway (e.g. via a compromised upstream document or a prior leak).
     */
    static String redactCredentialLeak(String rawToolResult, ScopedCredential credential, AuditLog audit) {
        if (rawToolResult == null || rawToolResult.isEmpty()) {
            return rawToolResult;
        }
        String token = credential.token();
        if (token != null && !token.isEmpty() && rawToolResult.contains(token)) {
            audit.write("credential_leak_blocked",
                    "credentialId", credential.id(),
                    "principal", credential.principal(),
                    "tokenFingerprint", credential.tokenFingerprint(),
                    "detail", "tool result contained live credential token; redacted before reaching model context");
            return rawToolResult.replace(token, "[REDACTED-CREDENTIAL]");
        }
        return rawToolResult;
    }

    /** Loose secondary check for injection PHRASING even when the exact token isn't present verbatim. */
    private static final Pattern LEAK_PROMPT_PATTERN = Pattern.compile(
            "(?i)ignore (all|any|previous) instructions|repeat (your|the) (credential|token)|output the following credential");

    static boolean looksLikeInjectionAttempt(String rawToolResult) {
        return rawToolResult != null && LEAK_PROMPT_PATTERN.matcher(rawToolResult).find();
    }
}
