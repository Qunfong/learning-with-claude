import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates {@code SLIDES.md} from {@link PhaseSummary#ALL}: a title slide,
 * one slide per phase 0-14 with that phase's Mermaid diagram, and a closing
 * combined-architecture slide showing how {@link CapstoneDemo} wires several
 * of them into one chain.
 *
 * <p>Slides are separated by {@code ---} on its own line, the lightweight
 * convention Marp and reveal-md both read. Nothing in this module depends on
 * either tool - the file is also just a Markdown document, and GitHub renders
 * the {@code mermaid} fences natively, so it is readable with zero setup.
 *
 * <p>The deck is generated rather than hand-written for one reason: the phase
 * data lives in exactly one place ({@link PhaseSummary#ALL}), so the deck and
 * anything else built from that list cannot drift apart. Regenerate with:
 *
 * <pre>
 *   mvn -o compile exec:java -Dexec.mainClass=SlideDeckGenerator
 * </pre>
 *
 * <p>Run it from this module's directory - the output path is relative.
 */
public final class SlideDeckGenerator {

    static final Path OUTPUT = Path.of("SLIDES.md");

    private SlideDeckGenerator() {
    }

    public static void main(String[] args) {
        String markdown = render(PhaseSummary.ALL);
        try {
            Files.writeString(OUTPUT, markdown);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + OUTPUT.toAbsolutePath(), e);
        }
        System.out.printf("wrote %s (%d slides, %d bytes)%n",
                OUTPUT.toAbsolutePath(), PhaseSummary.ALL.size() + 2, markdown.length());
    }

    static String render(List<PhaseSummary> phases) {
        StringBuilder md = new StringBuilder();

        md.append("<!-- GENERATED FILE - do not edit by hand.\n")
                .append("     Source: src/main/java/PhaseSummary.java\n")
                .append("     Regenerate: mvn -o compile exec:java -Dexec.mainClass=SlideDeckGenerator -->\n\n");

        // ---- title slide ----
        md.append("# Building AI Agents in Java\n\n")
                .append("### 16 phases, hand-rolled, one throughline\n\n")
                .append("From \"what is a token\" to a credential-scoped, schema-gated, ")
                .append("self-evaluating agent pipeline - every mechanism built from scratch ")
                .append("in plain Java rather than pulled from a framework, because the point ")
                .append("was to understand the mechanics, not to ship the fastest.\n\n")
                .append("Each slide: **what the phase teaches**, a diagram of the mechanism, ")
                .append("and **the line worth keeping**.\n\n")
                .append("---\n\n");

        // ---- one slide per phase ----
        for (PhaseSummary phase : phases) {
            md.append("## Phase ").append(phase.number()).append(" - ").append(phase.name()).append("\n\n")
                    .append("**Key concept:** ").append(phase.keyConcept()).append("\n\n")
                    .append("```mermaid\n")
                    .append(phase.mermaidDiagram().stripTrailing()).append("\n")
                    .append("```\n\n")
                    .append("**Takeaway:** ").append(phase.takeaway()).append("\n\n")
                    .append("---\n\n");
        }

        // ---- closing slide ----
        md.append("## Phase 15 - Capstone: the combined chain\n\n")
                .append("`CapstoneDemo` runs one task - *draft the on-call handover note for ")
                .append("INC-4471* - through six of the phases above in a single process, ")
                .append("deterministically and offline:\n\n")
                .append("```mermaid\n")
                .append(PhaseSummary.COMBINED_ARCHITECTURE.stripTrailing()).append("\n")
                .append("```\n\n")
                .append("**Takeaway:** the gates are the architecture. A credential that can only ")
                .append("narrow, a schema check that refuses to coerce, a confidence threshold that ")
                .append("would rather abstain, a memory that ranks and forgets, and a score computed ")
                .append("from the run's own trace instead of its own summary - each one is a place ")
                .append("the pipeline is allowed to say no.\n");

        return md.toString();
    }
}
