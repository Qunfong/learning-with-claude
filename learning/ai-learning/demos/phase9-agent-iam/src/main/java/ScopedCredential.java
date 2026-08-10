import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A hand-rolled analog of an AWS STS AssumeRole session credential — NOT real
 * AWS IAM, just the shape of the pattern: a principal chain (who is actually
 * acting, all the way back to the human), a scope set (what it's allowed to
 * do, the "policy" in miniature), and a hard expiry (time-bounded, exactly
 * like an STS session token).
 *
 * <p>The {@code token} field is the one piece of this record that must
 * NEVER be placed into an LLM prompt or response — see
 * {@code SecureToolInvoker} for how invocation stays out-of-band, and
 * {@link Guardrails#redactCredentialLeak} for the defense if a tool result
 * ever tries to smuggle it back into the conversation (prompt-injection-as-
 * credential-theft, see {@code AgentIamDemo}'s {@code --injection-theft}).
 *
 * @param id             opaque credential id, safe to log
 * @param principalChain EndUser -&gt; OrchestratorAgent -&gt; SpecializedAgent -&gt; Tool,
 *                        oldest first; {@link #principal()} is the last (current) actor
 * @param grantedScopes  the capability strings this credential authorizes
 * @param issuedAt       mint time
 * @param expiresAt      hard expiry — {@link #isExpired} is the only check that matters
 * @param token          the actual secret value; never log this, never put it in a prompt
 */
record ScopedCredential(String id, List<String> principalChain, Set<String> grantedScopes,
                         Instant issuedAt, Instant expiresAt, String token) {

    ScopedCredential {
        principalChain = List.copyOf(principalChain);
        grantedScopes = Set.copyOf(grantedScopes);
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    boolean hasScope(String scope) {
        return grantedScopes.contains(scope);
    }

    /** The current actor holding this credential (last link in the chain). */
    String principal() {
        return principalChain.get(principalChain.size() - 1);
    }

    /** Safe-to-log stand-in for the token: never write {@link #token} itself to any log line. */
    String tokenFingerprint() {
        String t = token == null ? "" : token;
        String suffix = t.length() >= 4 ? t.substring(t.length() - 4) : t;
        return "***" + suffix;
    }
}
