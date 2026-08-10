import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal re-implementation of {@code phase9-agent-iam/ScopedCredential} -
 * copied in and trimmed, not imported (no cross-module imports in this repo).
 *
 * <p>Trimmed vs. phase 9: no {@code issuedAt} (nothing in this module reads it)
 * and no audit-log integration. Everything load-bearing for the capstone chain
 * is kept: a principal chain, a scope set, a hard expiry, and a token that must
 * never reach the model's context.
 *
 * @param id             opaque credential id, safe to log
 * @param principalChain oldest first; {@link #principal()} is the current actor
 * @param grantedScopes  capability strings this credential authorizes
 * @param expiresAt      hard expiry
 * @param token          the secret; never logged, never put in a prompt
 */
public record ScopedCredential(String id, List<String> principalChain, Set<String> grantedScopes,
                               Instant expiresAt, String token) {

    public ScopedCredential {
        principalChain = List.copyOf(principalChain);
        // NOT Set.copyOf: its iteration order is randomized per JVM (SALT), which
        // would make this demo's printed scope lists differ between runs for no
        // reason. LinkedHashSet keeps insertion order, so the output is diffable.
        grantedScopes = Collections.unmodifiableSet(new LinkedHashSet<>(grantedScopes));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasScope(String scope) {
        return grantedScopes.contains(scope);
    }

    /** The current actor holding this credential (last link in the chain). */
    public String principal() {
        return principalChain.get(principalChain.size() - 1);
    }

    /** Safe-to-log stand-in for {@link #token}. */
    public String tokenFingerprint() {
        String t = token == null ? "" : token;
        return "***" + (t.length() >= 4 ? t.substring(t.length() - 4) : t);
    }

    public String chainString() {
        return String.join(" -> ", principalChain);
    }
}
