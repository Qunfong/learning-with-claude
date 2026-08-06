/** A2A task lifecycle states (see spec.md's Task Card). This demo only ever
 * produces DONE or FAILED (everything here is synchronous, in-process), but
 * SUBMITTED/WORKING are kept in the enum because they are meaningful the
 * moment a real transport (HTTP, a queue) makes "in progress" observable
 * from outside the call itself. */
enum TaskState {
    SUBMITTED,
    WORKING,
    DONE,
    FAILED
}
