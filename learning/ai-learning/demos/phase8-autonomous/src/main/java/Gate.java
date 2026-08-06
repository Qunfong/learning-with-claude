import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * GATE 1 / GATE 2: "blocks, waits for stdin: y/n + optional feedback", per
 * spec. Used TWICE by {@link AutonomousPipeline} -- once for plan approval,
 * once for PR approval -- with the exact same class, just a different
 * question string (same "one engine, different config" pattern as phase4's
 * AgentLoop).
 *
 * {@code autoApprove} is the "non-interactive flag for testing purposes" this
 * demo documents in README.md (CLI: {@code --auto}) -- without it, a real
 * human answering on stdin (or `echo y|`) drives every gate exactly as the
 * spec describes.
 */
class Gate {

    private final boolean autoApprove;
    private final BufferedReader in;

    Gate(boolean autoApprove) {
        this.autoApprove = autoApprove;
        this.in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    record Decision(boolean approved, String feedback) {
    }

    Decision ask(String question) {
        System.out.println();
        System.out.println("=== GATE: " + question + " (y/n) ===");

        if (autoApprove) {
            System.out.println("[--auto mode] auto-approving -> y");
            return new Decision(true, null);
        }

        System.out.print("> ");
        String line = readLineOrEmpty();
        boolean approved = line.strip().equalsIgnoreCase("y");

        String feedback = null;
        if (!approved) {
            System.out.print("Feedback (why rejected, optional): ");
            feedback = readLineOrEmpty();
            if (feedback.isBlank()) {
                feedback = "(no feedback given)";
            }
        }
        return new Decision(approved, feedback);
    }

    private String readLineOrEmpty() {
        try {
            String line = in.readLine();
            return line == null ? "" : line;
        } catch (Exception e) {
            System.out.println("(stdin read failed: " + e.getMessage() + " -- treating as empty)");
            return "";
        }
    }
}
