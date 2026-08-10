import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal re-implementation of {@code phase9-agent-iam/CredentialBroker} - the
 * STS-AssumeRole-chain analog, copied in and trimmed rather than imported.
 *
 * <p>The two invariants that make delegation mean anything are kept as hard
 * checks, not conventions:
 * <ol>
 *   <li>a delegated credential's scopes must be a SUBSET of its parent's -
 *       otherwise {@link IllegalArgumentException};</li>
 *   <li>a delegated credential's expiry is capped at the parent's -
 *       {@code min(now + ttl, parent.expiresAt())}.</li>
 * </ol>
 *
 * <p>Trimmed vs. phase 9: credential ids are a deterministic counter rather
 * than a random UUID, and tokens are a deterministic string rather than random
 * Base64, so this module's console output is byte-identical between runs (the
 * capstone doubles as a demo you can diff). That is a demo affordance, not a
 * security claim - a real broker must mint unguessable tokens.
 */
public final class CredentialBroker {

    private final AtomicInteger counter = new AtomicInteger();

    public ScopedCredential mintRoot(String principal, Set<String> scopes, Duration ttl, Instant now) {
        int n = counter.incrementAndGet();
        return new ScopedCredential(
                "cred-" + n,
                List.of(principal),
                new LinkedHashSet<>(scopes),
                now.plus(ttl),
                "tok-" + n + "-secret");
    }

    /**
     * Derives a narrower credential for the next principal in the chain.
     * Narrows or holds; never widens.
     */
    public ScopedCredential delegate(ScopedCredential parent, String toPrincipal,
                                     Set<String> requestedScopes, Duration ttl, Instant now) {
        Set<String> notHeld = new LinkedHashSet<>(requestedScopes);
        notHeld.removeAll(parent.grantedScopes());
        if (!notHeld.isEmpty()) {
            throw new IllegalArgumentException(
                    "delegation cannot widen scope: parent " + parent.principal()
                            + " does not hold " + notHeld);
        }

        Instant requested = now.plus(ttl);
        Instant capped = requested.isAfter(parent.expiresAt()) ? parent.expiresAt() : requested;

        List<String> chain = new ArrayList<>(parent.principalChain());
        chain.add(toPrincipal);

        return new ScopedCredential(
                "cred-" + counter.incrementAndGet(),
                chain,
                new LinkedHashSet<>(requestedScopes),
                capped,
                "tok-" + counter.get() + "-secret");
    }
}
