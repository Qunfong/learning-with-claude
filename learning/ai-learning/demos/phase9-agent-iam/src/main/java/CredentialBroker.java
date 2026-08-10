import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hand-rolled analog of AWS STS's {@code AssumeRole} chain — mints
 * {@link ScopedCredential}s and, critically, supports minting a
 * <b>narrower derived credential from a parent</b> (delegation) instead of
 * ever handing out the parent credential itself (impersonation). This is
 * the pattern AWS Module 8 calls "delegation vs. impersonation" and is the
 * single mechanism behind the blast-radius comparison in
 * {@code AgentIamDemo --delegation-vs-impersonation}.
 *
 * <p><b>This is the pattern, not real AWS IAM.</b> There's no policy
 * language, no permission boundaries, no cross-account trust — just the
 * core idea: every credential is scoped, time-bounded, and traceable back
 * through a principal chain to the human who ultimately authorized it.
 */
class CredentialBroker {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuditLog audit;

    CredentialBroker(AuditLog audit) {
        this.audit = audit;
    }

    /** Mint a root credential directly for an end user — the top of every principal chain. */
    ScopedCredential mintRoot(String principal, Set<String> scopes, Duration ttl) {
        Instant now = Instant.now();
        ScopedCredential cred = new ScopedCredential(
                newId(), List.of(principal), scopes, now, now.plus(ttl), newToken());
        audit.write("mint",
                "credentialId", cred.id(),
                "principal", cred.principal(),
                "principalChain", String.join(">", cred.principalChain()),
                "scopes", String.join(",", cred.grantedScopes()),
                "expiresAt", cred.expiresAt().toString(),
                "delegatedFrom", "");
        return cred;
    }

    /**
     * Mint a derived credential from a parent — the delegation path. {@code childScopes}
     * MUST be a subset of {@code parent}'s granted scopes (least privilege: a delegate
     * can never end up with more power than the credential it was derived from), and the
     * derived expiry is capped at the parent's expiry (a delegate can never outlive its
     * parent). Throws {@link IllegalArgumentException} on either violation — this is a
     * programming error in the caller, not a runtime authorization decision, so it's not
     * routed through the audit log as a "reject" (nothing was minted).
     */
    ScopedCredential delegate(ScopedCredential parent, String childPrincipal, Set<String> childScopes, Duration ttl) {
        if (!parent.grantedScopes().containsAll(childScopes)) {
            throw new IllegalArgumentException(
                    "delegated scopes " + childScopes + " are not a subset of parent scopes " + parent.grantedScopes());
        }
        Instant now = Instant.now();
        Instant requested = now.plus(ttl);
        Instant capped = requested.isBefore(parent.expiresAt()) ? requested : parent.expiresAt();

        List<String> chain = new ArrayList<>(parent.principalChain());
        chain.add(childPrincipal);

        ScopedCredential cred = new ScopedCredential(newId(), chain, childScopes, now, capped, newToken());
        audit.write("mint",
                "credentialId", cred.id(),
                "principal", cred.principal(),
                "principalChain", String.join(">", cred.principalChain()),
                "scopes", String.join(",", cred.grantedScopes()),
                "expiresAt", cred.expiresAt().toString(),
                "delegatedFrom", parent.id());
        return cred;
    }

    private static String newId() {
        return "cred-" + UUID.randomUUID();
    }

    private static String newToken() {
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
