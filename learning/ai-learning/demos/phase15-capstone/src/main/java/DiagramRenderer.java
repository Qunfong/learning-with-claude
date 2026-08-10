import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A tiny hand-rolled SVG builder for the deck's diagrams - same "build the
 * mechanism, do not import it" discipline as the rest of this curriculum.
 *
 * <p>Everything it emits is deliberately inside the subset GitHub's Markdown
 * sanitizer allows for an {@code <img src="*.svg">}: native shapes and
 * {@code <text>} only. No {@code <script>}, no {@code <style>}, no
 * {@code <foreignObject>}, no {@code <defs>/<marker>} (some sanitizers drop
 * {@code marker-end} references, so arrowheads are drawn as inline
 * {@code <polygon>} triangles), no external font or image references.
 *
 * <p><b>Light/dark safety.</b> The canvas is left transparent - a full-canvas
 * white rect would flash as a bright slab on GitHub's dark theme. Instead every
 * node carries its own opaque light fill with dark ink text, so each shape is
 * readable on either background, and edge labels sit on opaque "pills" for the
 * same reason.
 *
 * <p><b>Layout is hand-placed, and checked.</b> Callers give centre coordinates;
 * box and diamond sizes are computed from the text so a label can never outgrow
 * its shape. {@link #finish()} then verifies that no two shapes overlap, that no
 * label pill lands on a shape, and that nothing falls outside the viewBox - a
 * bad layout fails the build instead of shipping a mangled picture.
 */
public final class DiagramRenderer {

    // ---- one palette for all 16 diagrams -------------------------------
    // Mirrors the repo's site palette (docs/index.html): --bg-raised, --fg,
    // --line. Accent is chosen per node *type*, never per phase, so a red
    // shape means "this run stops here" in every diagram in the deck.
    static final String INK = "#1c1f1c";
    static final String INK_DIM = "#565f5a";
    static final String PAPER = "#fffdf9";
    static final String LINE = "#ddd8cd";
    static final String EDGE = "#6f7570";
    static final String AMBER = "#a8630a";
    static final String AMBER_FILL = "#fdf3e6";
    static final String RED = "#b7412f";
    static final String RED_FILL = "#fbeeeb";
    static final String GREEN = "#147a4a";
    static final String GREEN_FILL = "#e9f5ef";
    static final String TEAL = "#0d6e82";
    static final String TEAL_FILL = "#e8f2f5";

    static final String FONT = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";

    // Metrics. CHAR is the advance width of one monospace glyph at FS; every
    // size in here is derived from it, which is what keeps text inside shapes.
    private static final double FS = 12.5;
    private static final double CHAR = 7.52;
    private static final double LH = 16;
    private static final double PAD_X = 14;
    private static final double PAD_Y = 11;
    private static final double FS_S = 11;
    private static final double CHAR_S = 6.62;
    private static final double LH_S = 14;

    /** Node roles. The colour is the role, not the phase. */
    public enum Kind {
        /** Ordinary step. Cream fill, warm-gray border. */
        PROCESS(PAPER, LINE, 1.4),
        /** Entry point or success terminal. */
        START(GREEN_FILL, GREEN, 1.6),
        /** Rejection / halt terminal - the run does not continue past here. */
        STOP(RED_FILL, RED, 1.6),
        /** Branch point. Drawn as a diamond. */
        DECISION(AMBER_FILL, AMBER, 1.6),
        /** A participant in a sequence diagram. */
        ACTOR(TEAL_FILL, TEAL, 1.6);

        final String fill;
        final String stroke;
        final double strokeWidth;

        Kind(String fill, String stroke, double strokeWidth) {
            this.fill = fill;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
        }
    }

    /** A point, in viewBox units. */
    public record Pt(double x, double y) {
    }

    /** A placed shape's bounding box, plus edge anchors to draw arrows to. */
    public record Box(double cx, double cy, double w, double h) {
        public Pt left() {
            return new Pt(cx - w / 2, cy);
        }

        public Pt right() {
            return new Pt(cx + w / 2, cy);
        }

        public Pt top() {
            return new Pt(cx, cy - h / 2);
        }

        public Pt bottom() {
            return new Pt(cx, cy + h / 2);
        }

        public Pt centre() {
            return new Pt(cx, cy);
        }

        /** Anchor at fractional offsets of the half-extents, e.g. {@code at(1,-0.5)}. */
        public Pt at(double fx, double fy) {
            return new Pt(cx + fx * w / 2, cy + fy * h / 2);
        }
    }

    /** A sequence-diagram participant: its header box and its vertical line. */
    public record Lifeline(String name, double x, Box head) {
    }

    private record Rect(String what, double x0, double y0, double x1, double y1) {
        boolean hits(Rect o, double tol) {
            return x0 < o.x1 - tol && o.x0 < x1 - tol && y0 < o.y1 - tol && o.y0 < y1 - tol;
        }
    }

    private final StringBuilder body = new StringBuilder();
    private final List<Rect> shapes = new ArrayList<>();
    private final List<Rect> pills = new ArrayList<>();
    private final double width;
    private final double height;
    private final String ariaLabel;
    private double lifelineBottom;

    public DiagramRenderer(double width, double height, String ariaLabel) {
        this.width = width;
        this.height = height;
        this.ariaLabel = ariaLabel;
    }

    // ---- shapes --------------------------------------------------------

    /** A rounded-rect node centred on {@code (cx, cy)}, auto-sized to its text. */
    public Box box(double cx, double cy, Kind kind, String... lines) {
        return box(cx, cy, kind, 0, lines);
    }

    /**
     * As {@link #box}, but never narrower than {@code minWidth}. Use it for a
     * stack of parallel alternatives: ragged edges on things that are meant to
     * be read as siblings look like an accident, not a distinction.
     */
    public Box box(double cx, double cy, Kind kind, double minWidth, String... lines) {
        double w = Math.max(minWidth, textWidth(lines) + 2 * PAD_X);
        double h = lines.length * LH + 2 * PAD_Y;
        rect(cx - w / 2, cy - h / 2, w, h, 6, kind.fill, kind.stroke, kind.strokeWidth, null);
        text(cx, cy, lines.length, FS, INK, "middle", lines);
        shapes.add(new Rect(lines[0], cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2));
        return new Box(cx, cy, w, h);
    }

    /**
     * A decision diamond centred on {@code (cx, cy)}. Sized generously on both
     * axes: a diamond narrows away from its centreline, so the label needs
     * roughly twice the room a rectangle would want.
     */
    public Box diamond(double cx, double cy, String... lines) {
        double a = textWidth(lines) / 2 * 1.7 + 10;
        double b = 2.6 * (lines.length * LH / 2) + 12;
        String pts = f(cx) + "," + f(cy - b) + " " + f(cx + a) + "," + f(cy)
                + " " + f(cx) + "," + f(cy + b) + " " + f(cx - a) + "," + f(cy);
        body.append("  <polygon points=\"").append(pts).append("\" fill=\"").append(Kind.DECISION.fill)
                .append("\" stroke=\"").append(Kind.DECISION.stroke)
                .append("\" stroke-width=\"").append(f(Kind.DECISION.strokeWidth))
                .append("\" stroke-linejoin=\"round\"/>\n");
        text(cx, cy, lines.length, FS, INK, "middle", lines);
        shapes.add(new Rect(lines[0], cx - a, cy - b, cx + a, cy + b));
        return new Box(cx, cy, 2 * a, 2 * b);
    }

    // ---- arrows --------------------------------------------------------

    /** Straight arrow from {@code a} to {@code b}. */
    public void arrow(Pt a, Pt b) {
        segments(false, a, b);
    }

    /** Straight arrow carrying a label pill at its midpoint. */
    public void arrow(Pt a, Pt b, String label) {
        segments(false, a, b);
        pill((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, INK_DIM, label);
    }

    /** Dashed arrow - a return / response edge. */
    public void dashedArrow(Pt a, Pt b) {
        segments(true, a, b);
    }

    /**
     * An elbowed arrow through the given waypoints; the head lands on the last
     * point. Used for loop-backs and for edges that have to route around a box.
     */
    public void elbow(Pt... pts) {
        segments(false, pts);
    }

    /** Elbowed arrow plus a label pill placed explicitly on one of its legs. */
    public void elbow(Pt labelAt, String label, Pt... pts) {
        segments(false, pts);
        pill(labelAt.x(), labelAt.y(), INK_DIM, label);
    }

    private void segments(boolean dashed, Pt... pts) {
        for (int i = 0; i < pts.length - 2; i++) {
            line(pts[i], pts[i + 1], dashed);
        }
        Pt from = pts[pts.length - 2];
        Pt to = pts[pts.length - 1];
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double len = Math.hypot(dx, dy);
        if (len < 0.001) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double headLen = 9.5;
        double headHalf = 4.6;
        Pt base = new Pt(to.x() - ux * headLen, to.y() - uy * headLen);
        line(from, base, dashed);
        double nx = -uy;
        double ny = ux;
        body.append("  <polygon points=\"")
                .append(f(to.x())).append(",").append(f(to.y())).append(" ")
                .append(f(base.x() + nx * headHalf)).append(",").append(f(base.y() + ny * headHalf)).append(" ")
                .append(f(base.x() - nx * headHalf)).append(",").append(f(base.y() - ny * headHalf))
                .append("\" fill=\"").append(EDGE).append("\"/>\n");
    }

    private void line(Pt a, Pt b, boolean dashed) {
        body.append("  <line x1=\"").append(f(a.x())).append("\" y1=\"").append(f(a.y()))
                .append("\" x2=\"").append(f(b.x())).append("\" y2=\"").append(f(b.y()))
                .append("\" stroke=\"").append(EDGE).append("\" stroke-width=\"1.5\"");
        if (dashed) {
            body.append(" stroke-dasharray=\"6 4\"");
        }
        body.append("/>\n");
    }

    // ---- labels and regions --------------------------------------------

    /** An opaque label pill centred on a point - readable on any page background. */
    public void pill(double cx, double cy, String colour, String... lines) {
        double w = textWidth(lines, CHAR_S) + 16;
        double h = lines.length * LH_S + 12;
        rect(cx - w / 2, cy - h / 2, w, h, 3, PAPER, LINE, 1.0, null);
        text(cx, cy, lines.length, FS_S, colour, "middle", lines);
        pills.add(new Rect(lines[0], cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2));
    }

    /**
     * A tinted, dashed region grouping several nodes, with a title pill riding
     * its top-left corner. Regions are not collision-checked against nodes -
     * they are meant to contain them.
     */
    public void group(double x, double y, double w, double h, String accent, String title) {
        rect(x, y, w, h, 10, accent, accent, 1.3, "6 4");
        double tw = title.length() * CHAR_S + 16;
        double th = LH_S + 12;
        rect(x + 14, y - th / 2 + 9, tw, th, 3, PAPER, accent, 1.1, null);
        text(x + 14 + tw / 2, y + 9, 1, FS_S, accent, "middle", title);
        pills.add(new Rect(title, x + 14, y - th / 2 + 9, x + 14 + tw, y + th / 2 + 9));
    }

    // ---- sequence-diagram helpers --------------------------------------

    /**
     * Places participant heads left to right and drops a dashed lifeline from
     * each down to {@code bottom}. Heads are auto-sized, so {@code gap} is the
     * space between them, not their pitch.
     */
    public Lifeline[] lifelines(double headCy, double leftEdge, double gap, double bottom, String... names) {
        this.lifelineBottom = bottom;
        Lifeline[] out = new Lifeline[names.length];
        double x = leftEdge;
        for (int i = 0; i < names.length; i++) {
            double w = names[i].length() * CHAR + 2 * PAD_X;
            double cx = x + w / 2;
            Box head = box(cx, headCy, Kind.ACTOR, names[i]);
            body.append("  <line x1=\"").append(f(cx)).append("\" y1=\"").append(f(headCy + head.h() / 2))
                    .append("\" x2=\"").append(f(cx)).append("\" y2=\"").append(f(bottom))
                    .append("\" stroke=\"").append(EDGE)
                    .append("\" stroke-width=\"1.2\" stroke-dasharray=\"5 5\" stroke-opacity=\"0.7\"/>\n");
            out[i] = new Lifeline(names[i], cx, head);
            x = cx + w / 2 + gap;
        }
        return out;
    }

    /**
     * A horizontal message between two lifelines at height {@code y}. Solid =
     * a call, dashed = a return. The label pill sits just above the line and is
     * opaque, so it masks any lifeline it crosses.
     */
    public void message(Lifeline from, Lifeline to, double y, boolean isReturn, String... label) {
        double dir = Math.signum(to.x() - from.x());
        Pt a = new Pt(from.x() + dir * 3, y);
        Pt b = new Pt(to.x() - dir * 3, y);
        segments(isReturn, a, b);
        double h = label.length * LH_S + 12;
        pill((from.x() + to.x()) / 2, y - h / 2 - 5, isReturn ? INK_DIM : INK, label);
    }

    /** The y a lifeline stops at, for callers that want to sanity-check spacing. */
    public double lifelineBottom() {
        return lifelineBottom;
    }

    // ---- output --------------------------------------------------------

    /** Serialises the SVG, after checking the layout actually holds together. */
    public String finish() {
        validate();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(f(width)).append(" ").append(f(height)).append("\" width=\"").append(f(width))
                .append("\" height=\"").append(f(height))
                .append("\" role=\"img\" aria-label=\"").append(esc(ariaLabel)).append("\">\n")
                .append("  <title>").append(esc(ariaLabel)).append("</title>\n")
                .append(body)
                .append("</svg>\n");
        return svg.toString();
    }

    private void validate() {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                if (shapes.get(i).hits(shapes.get(j), 1)) {
                    problems.add("shapes overlap: '" + shapes.get(i).what() + "' / '" + shapes.get(j).what() + "'");
                }
            }
        }
        for (Rect p : pills) {
            for (Rect s : shapes) {
                if (p.hits(s, 2)) {
                    problems.add("label '" + p.what() + "' lands on shape '" + s.what() + "'");
                }
            }
        }
        List<Rect> all = new ArrayList<>(shapes);
        all.addAll(pills);
        for (Rect r : all) {
            if (r.x0() < 2 || r.y0() < 2 || r.x1() > width - 2 || r.y1() > height - 2) {
                problems.add("'" + r.what() + "' falls outside the " + f(width) + "x" + f(height) + " canvas: "
                        + f(r.x0()) + "," + f(r.y0()) + " - " + f(r.x1()) + "," + f(r.y1()));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("bad layout in \"" + ariaLabel + "\":\n  " + String.join("\n  ", problems));
        }
    }

    // ---- primitives ----------------------------------------------------

    private void rect(double x, double y, double w, double h, double r,
                      String fill, String stroke, double sw, String dash) {
        body.append("  <rect x=\"").append(f(x)).append("\" y=\"").append(f(y))
                .append("\" width=\"").append(f(w)).append("\" height=\"").append(f(h))
                .append("\" rx=\"").append(f(r)).append("\" fill=\"").append(fill).append("\"");
        if (dash != null) {
            // a tinted region: the fill is the accent at low opacity, not a solid
            body.append(" fill-opacity=\"0.07\" stroke-opacity=\"0.5\" stroke-dasharray=\"").append(dash).append("\"");
        }
        body.append(" stroke=\"").append(stroke).append("\" stroke-width=\"").append(f(sw)).append("\"/>\n");
    }

    private void text(double cx, double cy, int lineCount, double size, String colour, String anchor, String... lines) {
        double first = cy - (lineCount - 1) * (size == FS ? LH : LH_S) / 2 + size * 0.36;
        double step = size == FS ? LH : LH_S;
        for (int i = 0; i < lines.length; i++) {
            body.append("  <text x=\"").append(f(cx)).append("\" y=\"").append(f(first + i * step))
                    .append("\" text-anchor=\"").append(anchor)
                    .append("\" font-family=\"").append(FONT)
                    .append("\" font-size=\"").append(f(size))
                    .append("\" fill=\"").append(colour).append("\">")
                    .append(esc(lines[i])).append("</text>\n");
        }
    }

    private static double textWidth(String[] lines) {
        return textWidth(lines, CHAR);
    }

    private static double textWidth(String[] lines, double charWidth) {
        int max = 0;
        for (String l : lines) {
            max = Math.max(max, l.length());
        }
        return max * charWidth;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
