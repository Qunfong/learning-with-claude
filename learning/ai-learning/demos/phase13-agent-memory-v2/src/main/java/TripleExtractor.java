import java.util.List;

/**
 * Extracts {@link GraphMemory.Triple}s out of plain-language facts (the kind
 * {@code MemoryStoreV2} stores). Two implementations, same interface — same
 * "swap the implementation, keep the contract" lesson as phase1's
 * ModelClient strategy pattern:
 *   - {@link RuleBasedTripleExtractor}: deterministic regex extraction, used
 *     in tests and by default, so this module stays fully offline/testable.
 *   - {@link OllamaTripleExtractor}: the "one LLM call" the task spec asks
 *     for, used only by {@link GraphMemoryDemo}'s live run when Ollama is
 *     reachable.
 */
interface TripleExtractor {
    List<GraphMemory.Triple> extract(List<String> facts);
}
