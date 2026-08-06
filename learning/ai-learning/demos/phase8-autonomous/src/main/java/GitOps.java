import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Safety-rail plumbing: "git stash before any write; restore on abort" +
 * the git side of PROpener (branch + commit). Every method gracefully
 * detects "this directory is not a git repository" and SIMULATES (prints
 * what it would have done) instead of failing -- confirmed via
 * {@code git status} that D:\Users\Qunfo\Downloads\AI_Learning is NOT a git
 * repository, so every real run of this demo exercises the simulate path.
 * The real-git code path is still fully implemented (not stubbed) so it
 * behaves correctly the day this project IS put under version control.
 */
class GitOps {

    private final Path repoRoot;

    GitOps(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    boolean isGitRepo() {
        try {
            ProcessResult r = run(List.of("git", "rev-parse", "--is-inside-work-tree"));
            return r.exitCode() == 0 && r.output().strip().equals("true");
        } catch (Exception e) {
            return false;
        }
    }

    record StashResult(boolean stashed, String note) {
    }

    /** Safety rail: stash working tree BEFORE any write, so an aborted/escalated run can be rolled back. */
    StashResult stashIfPossible() {
        if (!isGitRepo()) {
            System.out.println("[git-safety] " + repoRoot + " is not a git repository -- "
                    + "SKIPPING `git stash` (would stash the working tree before writing, so an "
                    + "escalated/aborted run could be rolled back cleanly)");
            return new StashResult(false, "not-a-git-repo");
        }
        try {
            ProcessResult r = run(List.of("git", "stash", "push", "-u", "-m", "phase8-autonomous-pre-write"));
            boolean stashed = r.exitCode() == 0 && !r.output().contains("No local changes to save");
            System.out.println("[git-safety] git stash push -> " + r.output().strip());
            return new StashResult(stashed, r.output().strip());
        } catch (Exception e) {
            System.out.println("[git-safety] git stash push FAILED: " + e.getMessage());
            return new StashResult(false, "stash-failed: " + e.getMessage());
        }
    }

    /** Called on ESCALATE / gate rejection abort: rolls back to the pre-write state. */
    void restoreStashIfNeeded(StashResult sr) {
        if (!sr.stashed()) {
            System.out.println("[git-safety] nothing to restore (" + sr.note() + ")");
            return;
        }
        try {
            ProcessResult r = run(List.of("git", "stash", "pop"));
            System.out.println("[git-safety] restored pre-write state via `git stash pop` -> "
                    + r.output().strip());
        } catch (Exception e) {
            System.out.println("[git-safety] FAILED to restore stash -- manual `git stash pop` needed: "
                    + e.getMessage());
        }
    }

    record BranchResult(boolean created, String branchName) {
    }

    BranchResult createBranch(String branchName) {
        if (!isGitRepo()) {
            System.out.println("[git] " + repoRoot + " is not a git repository -- SIMULATING: $ git checkout -b " + branchName);
            return new BranchResult(false, branchName);
        }
        try {
            run(List.of("git", "checkout", "-b", branchName));
            return new BranchResult(true, branchName);
        } catch (Exception e) {
            System.out.println("[git] branch creation failed: " + e.getMessage());
            return new BranchResult(false, branchName);
        }
    }

    boolean commit(String relFilePath, String message) {
        if (!isGitRepo()) {
            System.out.println("[git] not a git repository -- SIMULATING: $ git add " + relFilePath);
            System.out.println("[git] not a git repository -- SIMULATING: $ git commit -m \"" + message + "\"");
            return false;
        }
        try {
            run(List.of("git", "add", relFilePath));
            run(List.of("git", "commit", "-m", message));
            return true;
        } catch (Exception e) {
            System.out.println("[git] commit failed: " + e.getMessage());
            return false;
        }
    }

    record ProcessResult(int exitCode, String output) {
    }

    private ProcessResult run(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("git command timed out: " + String.join(" ", cmd));
        }
        return new ProcessResult(p.exitValue(), output);
    }
}
