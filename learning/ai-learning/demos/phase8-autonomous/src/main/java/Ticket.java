/**
 * The unit of work the autonomous pipeline processes, exactly as specified in
 * openspec/specs/phase8-autonomous/spec.md ("Build Specification / Input").
 *
 * @param targetFile path to the file the pipeline will read/modify, RELATIVE
 *                    to this module's working directory (i.e. run from
 *                    {@code demos/phase8-autonomous/}, same convention as
 *                    every other phase's sandboxed workspace path).
 */
record Ticket(String id, String title, String description, String targetFile) {
}
