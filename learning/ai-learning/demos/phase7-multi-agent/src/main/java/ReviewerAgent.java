import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReviewerAgent — A2A capability {@code code.review}.
 *
 * Two independent review passes, combined:
 *   1. STATIC rule check — a small, deliberately simple regex/line-based
 *      scanner covering a SUBSET of java-standards' R1-R10: R1 (swallowed
 *      exceptions), R2 (catches too generic), R6 (returns null), R8
 *      (System.out), R9 (method too long). Deterministic — same input
 *      always produces the same issues, unlike the LLM pass below.
 *   2. LLM review — Phase 5's exact pattern (skill.md content injected as
 *      the system prompt, see SkillsDemo#resolveSkillPath), asked to judge
 *      the code against R1-R10.
 *
 * Full R1-R10 coverage needs real semantic/AST analysis (is this actually
 * immutable? is this name intention-revealing? is this a magic number or a
 * legitimate literal?) that a regex scanner cannot judge reliably — those
 * (R3, R4, R5, R7, R10) are left entirely to the LLM pass. See README
 * "Open Questions" for what happens when the two passes disagree.
 */
class ReviewerAgent implements A2AAgent {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = "llama3.2:3b";
    private static final String SKILL_RELATIVE = "skills/java-standards/skill.md";

    /** Set -Dphase7.mock=true to run the whole demo without a live Ollama call —
     * see README "Fallback / mock mode". */
    private static final boolean MOCK = Boolean.getBoolean("phase7.mock");

    private final OllamaClient client;
    private final String skillContent;

    ReviewerAgent(OllamaClient client) {
        this.client = client;
        if (MOCK) {
            this.skillContent = "(mock mode — skill.md not loaded)";
        } else {
            try {
                this.skillContent = Files.readString(resolveSkillPath());
            } catch (IOException e) {
                throw new IllegalStateException("could not read java-standards skill: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public AgentCard card() {
        return new AgentCard("ReviewerAgent", List.of("code.review"), "in-process://reviewer-agent");
    }

    @Override
    public TaskResult handle(Task task) {
        if (!"code.review".equals(task.type())) {
            return TaskResult.failed(task.id(), "ReviewerAgent cannot handle task type '" + task.type() + "'");
        }
        String code = task.input().getOrDefault("code", "");
        if (code.isBlank()) {
            return TaskResult.failed(task.id(), "ReviewerAgent received no code to review");
        }

        List<Issue> staticIssues = staticCheck(code);
        String llmVerdict = llmReview(code);

        List<String> issues = new ArrayList<>();
        for (Issue issue : staticIssues) {
            issues.add("[static] line %d — %s: %s".formatted(issue.line(), issue.rule(), issue.message()));
        }
        issues.add("[llm] " + llmVerdict.replace("\n", " "));

        String annotated = annotate(code, staticIssues);
        Artifact artifact = new Artifact("java_file_annotated", annotated);
        String message = staticIssues.isEmpty()
                ? "static checks clean (LLM pass may still have found issues — see issues list)"
                : staticIssues.size() + " static issue(s) found";

        return new TaskResult(task.id(), TaskState.DONE, List.of(artifact), issues, message);
    }

    // ---- static rule check (subset of R1-R10) ------------------------------

    record Issue(String rule, int line, String message) {}

    private static final Pattern CATCH_BLOCK =
            Pattern.compile("catch\\s*\\(\\s*(\\w+)\\s+\\w+\\s*\\)\\s*\\{([^{}]*)\\}");
    private static final Pattern RETURN_NULL = Pattern.compile(".*\\breturn\\s+null\\s*;.*");
    private static final Pattern SYSTEM_OUT = Pattern.compile(".*System\\.out\\.(print|println)\\(.*");
    private static final Pattern METHOD_SIGNATURE =
            Pattern.compile("^\\s*(public|private|protected)[^;{]*\\)\\s*(throws[^{]*)?\\{\\s*$");
    private static final int MAX_METHOD_LINES = 20;

    static List<Issue> staticCheck(String code) {
        List<Issue> issues = new ArrayList<>();
        String[] lines = code.split("\n", -1);

        Matcher m = CATCH_BLOCK.matcher(code);
        while (m.find()) {
            int line = lineOf(code, m.start());
            String exceptionType = m.group(1);
            String body = m.group(2).strip();
            if (body.isEmpty()) {
                issues.add(new Issue("R1", line, "empty catch block swallows " + exceptionType));
            } else if (body.contains("printStackTrace")) {
                issues.add(new Issue("R1", line, "printStackTrace() swallows " + exceptionType + " (no rethrow)"));
            }
            if (exceptionType.equals("Exception") || exceptionType.equals("Throwable")) {
                issues.add(new Issue("R2", line, "catches generic " + exceptionType + " instead of a specific type"));
            }
        }

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (RETURN_NULL.matcher(l).matches()) {
                issues.add(new Issue("R6", i + 1, "returns null instead of Optional"));
            }
            if (SYSTEM_OUT.matcher(l).matches()) {
                issues.add(new Issue("R8", i + 1, "System.out used instead of a logger"));
            }
        }

        issues.addAll(checkMethodLength(lines));
        return issues;
    }

    private static List<Issue> checkMethodLength(String[] lines) {
        List<Issue> issues = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (METHOD_SIGNATURE.matcher(lines[i]).matches()) {
                int start = i;
                int depth = 1;
                int j = i + 1;
                while (j < lines.length && depth > 0) {
                    depth += countChar(lines[j], '{') - countChar(lines[j], '}');
                    j++;
                }
                int bodyLines = j - start - 1;
                if (bodyLines > MAX_METHOD_LINES) {
                    issues.add(new Issue("R9", start + 1, "method body is " + bodyLines + " lines (max ~" + MAX_METHOD_LINES + ")"));
                }
                i = j;
            } else {
                i++;
            }
        }
        return issues;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int k = 0; k < s.length(); k++) {
            if (s.charAt(k) == c) n++;
        }
        return n;
    }

    private static int lineOf(String text, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static String annotate(String code, List<Issue> issues) {
        List<String> out = new ArrayList<>(List.of(code.split("\n", -1)));
        // insert bottom-to-top so earlier issues' line numbers stay valid as we mutate
        issues.stream()
                .sorted((a, b) -> Integer.compare(b.line(), a.line()))
                .forEach(issue -> {
                    int idx = Math.min(Math.max(issue.line() - 1, 0), out.size());
                    out.add(idx, "// REVIEW[" + issue.rule() + "]: " + issue.message());
                });
        return String.join("\n", out);
    }

    // ---- LLM review, Phase 5's exact skill-injection pattern ---------------

    private String llmReview(String code) {
        if (MOCK) {
            System.out.println("  [mock] ReviewerAgent returning canned LLM verdict (phase7.mock=true, no Ollama call)");
            return "No violations found. (mock LLM review — no live Ollama call)";
        }
        ArrayNode messages = JSON.createArrayNode();
        messages.addObject().put("role", "system").put("content", skillContent);
        messages.addObject().put("role", "user").put("content", """
                Review this Java code against the java-standards rules (R1-R10) above.
                List ONLY violations you find, one per line, format 'R<n>: <one-sentence reason>'.
                If there are none, reply exactly: No violations found.

                ```java
                %s
                ```
                """.formatted(code));
        OllamaClient.ChatResult result = client.chat(MODEL, messages, JSON.createArrayNode());
        return result.message().path("content").asText("").strip();
    }

    // ---- resolve skill.md relative to this class's own location, Phase 5's
    // exact pattern (works under mvn exec:java regardless of cwd) -----------
    static Path resolveSkillPath() {
        Path start;
        try {
            start = Path.of(ReviewerAgent.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not determine class location", e);
        }
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SKILL_RELATIVE);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not find " + SKILL_RELATIVE + " above " + start
                        + " — run from within the repo, or fix SKILL_RELATIVE.");
    }
}
