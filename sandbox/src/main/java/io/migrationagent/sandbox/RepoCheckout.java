package io.migrationagent.sandbox;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Clones a public Git repository and checks out a specific commit SHA into
 * a local directory, using JGit rather than shelling out to a system
 * {@code git} binary — this runs on the host (not inside the sandbox
 * container), and a pure-Java implementation means it works the same way
 * regardless of what's installed on the machine running the CLI.
 *
 * <p>This does a full clone rather than a shallow one: the target SHA isn't
 * necessarily a branch tip (it's often an older commit chosen for a
 * benchmark), and JGit's shallow-clone support has edge cases fetching an
 * arbitrary historical commit. For the small repos this tool targets, a
 * full clone is fast enough that the extra complexity isn't worth it.
 */
public final class RepoCheckout {

    public Path checkout(String repoUrl, String commitSha, Path destination) throws IOException {
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(destination.toFile())
                .call()) {
            git.checkout().setName(commitSha).call();
        } catch (GitAPIException e) {
            throw new IOException("Failed to clone " + repoUrl + " and checkout " + commitSha, e);
        }
        return destination;
    }
}
