# Phase 5 — Skills & Domain Knowledge

## What This Phase Is About

Skills are packaged, reusable instructions + resources you inject into an agent.
The user already has real examples: `.claude/skills/` in this very repo.
Phase 5 makes that concrete: you build one yourself, for a domain you know cold (Java).

---

## Core Concepts

### Skills vs RAG vs Fine-tuning — decision matrix

```
                 SKILLS          RAG              FINE-TUNING
─────────────────────────────────────────────────────────────────
What it is    Curated docs    Dynamic retrieval  Baked into weights
              + instructions  from large corpus

Update speed  Instant edit    Instant (re-index) Days (retrain)
              of skill.md     if corpus changes

Best for      Stable,         Large, changing    Style/tone/format
              procedural      reference docs     at model level
              knowledge       (codebase, docs)

Cost          Free             Index + query      Expensive + slow
              (prompt tokens   cost               to iterate
              only)

Controllable  Very high        Medium             Low (opaque)
              (you wrote it)   (retrieval quality) (weight change)

─────────────────────────────────────────────────────────────────
RULE OF THUMB:
- Coding standards, workflows, house style → SKILL
- "Answer questions about our 500 Java files" → RAG
- "Always respond in formal Dutch" → Fine-tuning (or system prompt)
```

### Anatomy of a Skill

```
skills/
  java-standards/
    skill.md          ← instructions (WHAT the agent must do/avoid)
    examples/
      good.java       ← positive example
      bad.java        ← anti-pattern with annotation
    resources/
      checkstyle.xml  ← reference the agent can cite
      arch-rules.md   ← architecture decisions
```

`skill.md` structure:
1. **Trigger** — when this skill activates
2. **Rules** — concrete, numbered, testable
3. **Examples** — good/bad, annotated
4. **Anti-patterns** — what NOT to do and why

---

## Build Specification

### Goal
A skill called `java-standards` that encodes your team's Java coding standards.
Inject it into the Phase 4 agent and verify it produces better output than without.

### Deliverables

**1. `skill.md`** — at minimum covers:
- Naming conventions (classes, methods, variables)
- Exception handling (no swallowed exceptions, no `catch (Exception e) {}`)
- Logging standards (which framework, what to log)
- Immutability preferences (prefer records, final fields)
- Test naming (`shouldDoX_whenY()` pattern or your preference)

**2. Agent integration test**
- Task without skill: "Write a Java class that reads a file"
- Same task with skill injected → compare outputs
- Agent should call out violations proactively

**3. Skill quality rubric**
Rate your skill on:
- Is each rule testable? (can you write a unit test for it?)
- Is each rule justified? (WHY, not just WHAT)
- Are the examples realistic? (not toy code)

### Key Learning Moment
Run the same coding task through Phase 4 agent:
- Without skill → generic Java
- With skill → follows your standards

Diff the outputs. That diff is the value of the skill.

---

## Open Questions to Explore

1. How do you version a skill? What's your update workflow?
2. What happens when skill rules conflict with each other?
3. At what size does a skill become too large to fit in context?
   (Hint: chunk it like RAG — topic-specific sub-skills)
4. Can a skill reference another skill? (Composition)

---

## Success Criteria

- [ ] Skill has ≥ 8 concrete, testable rules
- [ ] Each rule has an annotated example
- [ ] Agent with skill rejects at least one anti-pattern proactively
- [ ] You can explain the Skills vs RAG vs Fine-tuning decision for 3 concrete scenarios
- [ ] Slide: "Skills vs RAG vs Fine-tuning" with your own decision matrix

---

## Dependencies

- Phase 4: agent loop (skill gets injected into this agent)

## Estimated Effort

2–4 hours (heavy on thinking/writing, light on code)
