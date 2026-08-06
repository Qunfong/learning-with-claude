import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PR OPEN state: "git branch + commit + gh pr create", per spec.
 *
 * This directory (D:\Users\Qunfo\Downloads\AI_Learning) is confirmed NOT a
 * git repository (`git status` -> "fatal: not a git repository"). Per the
 * task brief, this gracefully detects that and SIMULATES/PRINTS what it
 * WOULD do instead of failing -- it never throws just because there's no
 * repo. Also detects whether the `gh` CLI is installed, independent of the
 * git-repo check, and reports both conditions clearly.
 */
class PROpener {

    private final Path repoRoot;
    private final GitOps gitOps;

    PROpener(Path repoRoot, GitOps gitOps) {
        this.repoRoot = repoRoot;
        this.gitOps = gitOps;
    }

    record PrResult(boolean simulated, String branch, String url) {
    }

    PrResult openPr(Ticket ticket, String targetFile, String reviewSummary) {
        boolean isRepo = gitOps.isGitRepo();
        boolean ghAvailable = isGhAvailable();
        String branch = "auto/" + ticket.id().toLowerCase();
        String commitMessage = ticket.id() + ": " + ticket.title();
        String prBody = "Automated change by the phase8-autonomous pipeline.\n\n"
                + "Ticket: " + ticket.id() + " - " + ticket.title() + "\n"
                + ticket.description() + "\n\n"
                + "Review summary:\n" + reviewSummary;

        if (!isRepo) {
            System.out.println();
            System.out.println("[pr-opener] " + repoRoot + " is NOT a git repository -- SIMULATING what "
                    + "would happen (real commands below are printed, not executed):");
            System.out.println("  $ git checkout -b " + branch);
            System.out.println("  $ git add " + targetFile);
            System.out.println("  $ git commit -m \"" + commitMessage + "\"");
            if (ghAvailable) {
                System.out.println("  $ gh pr create --title \"" + commitMessage + "\" --body \"...\"");
                System.out.println("  (note: `gh` CLI IS installed, but PR creation still needs a real git "
                        + "repo + remote to push to, so this stays simulated)");
            } else {
                System.out.println("  $ gh pr create --title \"" + commitMessage + "\" --body \"...\"");
                System.out.println("  (note: `gh` CLI was not found either -- would also need installing)");
            }
            return new PrResult(true, branch, "(simulated) no PR opened -- " + repoRoot + " is not a git repository");
        }

        // Real path -- not exercised in this environment (confirmed not a git repo above),
        // but implemented for completeness / the day this project is put under version control.
        GitOps.BranchResult branchResult = gitOps.createBranch(branch);
        boolean committed = gitOps.commit(targetFile, commitMessage);
        if (!branchResult.created() || !committed) {
            return new PrResult(true, branch, "(simulated) git operations failed, see log above");
        }
        if (!ghAvailable) {
            System.out.println("[pr-opener] `gh` CLI not found -- SIMULATING: $ gh pr create --title \""
                    + commitMessage + "\" --body \"...\"");
            return new PrResult(true, branch, "(simulated) branch/commit done, but `gh` CLI is not installed");
        }

        try {
            ProcessResult r = run(List.of("gh", "pr", "create", "--title", commitMessage, "--body", prBody));
            String url = r.output().strip();
            return new PrResult(false, branch, url);
        } catch (Exception e) {
            System.out.println("[pr-opener] `gh pr create` failed: " + e.getMessage());
            return new PrResult(true, branch, "(simulated) gh pr create failed: " + e.getMessage());
        }
    }

    static boolean isGhAvailable() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "gh", "--version")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
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
            throw new RuntimeException("gh command timed out: " + String.join(" ", cmd));
        }
        return new ProcessResult(p.exitValue(), output);
    }
}
