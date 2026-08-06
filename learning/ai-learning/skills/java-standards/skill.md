# Java Coding Standards Skill

## Trigger
Activate whenever writing, reviewing, or evaluating Java code.
These rules apply to ALL Java you produce — no exceptions for "demo code" or "quick fixes".

---

## Rules

### R1 — Exception Handling: Never Swallow
NEVER: `catch (Exception e) {}` or `catch (Exception e) { e.printStackTrace(); }`
DO: throw wrapped, or log + rethrow with original cause.
WHY: swallowed exceptions vanish silently in production. Stack trace is your only evidence.

```java
// BAD
catch (IOException e) { e.printStackTrace(); }

// GOOD
catch (IOException e) { throw new UncheckedIOException("Failed to read: " + path, e); }
```

### R2 — Catch Specific, Not General
Catch the narrowest exception type that makes sense (`IOException`, not `Exception`).
WHY: catching `Exception` masks bugs like `NullPointerException` that you should fix, not swallow.

### R3 — Immutability First
Prefer `record` for data carriers. All fields `final` unless mutation is explicitly required.
WHY: immutable = thread-safe by default + no defensive copy needed + easier to test.

```java
// BAD
class Person { String name; int age; }

// GOOD
record Person(String name, int age) {}
```

### R4 — No Magic Values
Every literal that carries meaning → named `static final` constant (ALL_CAPS).
WHY: one change in one place, not a grep across the codebase.

```java
// BAD
HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))

// GOOD
static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
```

### R5 — Intention-Revealing Names
- Variables/methods: camelCase. Methods are verbs (`processLine`, not `line`).
- Fields: nouns (`errorCount`, not `n` or `cnt`).
- Classes: PascalCase noun (`CsvParser`, not `ParseCsv`).
- No single-letter vars outside loop indexes. No abbreviations unless domain-universal (`id`, `dto`).

### R6 — Null: Use Optional, Not null Returns
Never return `null` from a public method. Return `Optional<T>` for absent values.
WHY: null propagates silently across call boundaries. Optional forces the caller to handle absence.

```java
// BAD
public User findUser(String id) { return null; }

// GOOD
public Optional<User> findUser(String id) { return Optional.ofNullable(cache.get(id)); }
```

### R7 — Fail Fast: Validate Preconditions at Entry
Use `Objects.requireNonNull(param, "param must not be null")` at the top of public methods.
WHY: the closer the failure is to the bad input, the faster you find the bug.

### R8 — No System.out in Production Code
Use SLF4J (`LoggerFactory.getLogger(getClass())`). Never `System.out.println` outside demos.
Log WHAT happened + relevant IDs. Not HOW (the code shows that).
Levels: ERROR=needs fix, WARN=unexpected but recovered, INFO=business event, DEBUG=internal detail.

### R9 — Method Size: Max ~20 Lines
If a method doesn't fit on one screen, extract a helper with a descriptive name.
WHY: small methods = easy to test, easy to name, easy to understand in isolation.

### R10 — Test Naming: shouldDoX_whenY
Pattern: `void shouldReturnEmpty_whenFileNotFound()`.
WHY: the test name IS the documentation. A failing test name tells you exactly what broke.

---

## Anti-Pattern Summary

| ❌ Anti-pattern                          | ✓ Correct                                              |
|------------------------------------------|--------------------------------------------------------|
| `catch (Exception e) {}`                 | `catch (IOException e) { throw new Unchecked...(e); }` |
| `return null`                            | `return Optional.empty()`                              |
| `int MAX = 100` in method body           | `static final int MAX_RETRIES = 100;`                  |
| `class Person { String name; }`          | `record Person(String name) {}`                        |
| `System.out.println("done")`             | `log.info("Processing complete. lines={}", count)`     |
| `public void proc(String s)`             | `public void processLine(String rawLine)`              |
| `catch (Exception e)` for specific error | `catch (IOException e)` — be specific                  |

---

## Skill Scope
Covers: naming, exceptions, immutability, nulls, constants, logging, method size, test naming.
Does NOT cover: architecture patterns, concurrency, performance (separate skills).
