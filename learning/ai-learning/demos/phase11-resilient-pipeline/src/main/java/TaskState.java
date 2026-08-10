/** A2A task lifecycle states -- copied from phase7-multi-agent/TaskState.java
 * (not imported; every phase is an independent Maven module). This demo only
 * ever produces DONE or FAILED (everything here is synchronous, in-process),
 * but SUBMITTED/WORKING are kept because {@link HandoffSchema} validates the
 * "state" field against this exact enum -- a handoff claiming a fifth,
 * made-up state is exactly the kind of malformed payload the schema check
 * must reject. */
enum TaskState {
    SUBMITTED,
    WORKING,
    DONE,
    FAILED
}
