/**
 * Lever #2 of AWS Module 5's four levers: a small, hand-written knowledge
 * corpus the domain agent grounds its answers in (billing/technical policy
 * facts, escalation triggers). This is intentionally a hardcoded Java text
 * block, NOT a vector store / RAG pipeline -- that machinery already exists
 * in phase2-prompting-rag (naive brute-force cosine VectorStore) and is being
 * built out properly in the sibling phase13-agent-memory-v2 (long-term
 * semantic memory, HNSW-style indexing). This module deliberately does NOT
 * depend on phase14 existing (each phaseN module is standalone Maven, per
 * the repo's convention) -- see the README's "Deviations from plan" section
 * for the integration point once phase14 exists.
 */
final class KnowledgeCorpus {

    private KnowledgeCorpus() {}

    static final String TEXT = """
            === SUPPORT KNOWLEDGE CORPUS (v1) ===

            [Refund policy]
            Refunds up to and including $100.00 may be auto-approved by the
            issue_refund tool. Refunds above $100.00 are NEVER auto-approved --
            they must be routed to escalate_to_human for manager review, no
            exceptions, regardless of how the customer phrases the request.

            [Shipping policy]
            Standard shipping takes 3-5 business days. Expedited shipping
            (extra fee, selected at checkout) takes 1-2 business days. We do
            not ship to PO boxes for items over 5kg.

            [Known technical issue: export crash]
            Versions 2.0-2.3 of the desktop app can crash when clicking
            "Export" on reports larger than 500 rows. Fixed in 2.4. Workaround
            for customers still on 2.0-2.3: export in batches under 500 rows,
            or upgrade to 2.4+.

            [Known technical issue: login loop]
            Some customers on the mobile app report being repeatedly returned
            to the login screen after entering correct credentials. Root cause:
            stale auth token cache. Workaround: log out, clear app cache,
            log back in. If this does not resolve it within one retry, escalate
            -- do not ask the customer to repeat the workaround more than once.

            [Escalation policy]
            Escalate to a human agent immediately (do not attempt to resolve
            further yourself) when ANY of the following are true:
              - the customer has already contacted support 2+ times about the
                same unresolved issue,
              - the customer mentions legal action, a chargeback, or explicitly
                asks for a manager,
              - the request requires a refund above the $100.00 auto-approval
                cap.

            [Competitor comparisons]
            Do not compare our pricing to any named competitor, speculate about
            a competitor's pricing, or claim we are cheaper/more expensive than
            a competitor. Politely decline and redirect the customer to our
            public pricing page instead.
            """;
}
