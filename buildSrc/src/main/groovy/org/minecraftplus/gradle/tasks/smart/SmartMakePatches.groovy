package org.minecraftplus.gradle.tasks.smart

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import uk.jamierocks.propatcher.task.MakePatchesTask

class SmartMakePatches extends MakePatchesTask {

    @Input String branch
    @Input String commitId
    @Input gitrepo //TODO Annotating this as @InputDirectory causes wrong task dependency resolution with commitTask

    @TaskAction
    void doTask() {

        try (Git git = Git.open(gitrepo)) {
            def repository = git.getRepository()

            // Check repository state - we need to be in safe checked-out state
            def state = repository.getRepositoryState();
            logger.trace("Repository in state: {}", state.toString())
            switch(state) {
                case RepositoryState.SAFE: {
                    break
                }
                default:
                    throw new IllegalStateException("Repository need be in safe state!")
            }

            // Check actual branch - we need be in branch from smart config
            def actualBranch = repository.getBranch()
            logger.trace("Actual branch: {}", actualBranch)
            if (actualBranch != branch) {
                throw new IllegalStateException("Target repository need be checked-out on branch " + branch)
            }

            // Checkout to specified commit and generate smartpatches
            // After process repository will be checked out to starting position
            try {
                // Checkout smart commit id
                git.checkout().setName(commitId).call();
                makePatches()
                logger.lifecycle("Patches maked in {}", patches)
            } finally {
                git.checkout().setName(actualBranch).call();
            }
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }
    }

    void makePatches() {
        if (!patches.exists())
            patches.mkdirs()

        def root = rootZip == null ? rootDir : rootZip
        if (root == null)
            throw new InvalidUserDataException("At least one of rootZip and rootDir has to be specified!")
        process(root, target) // Make the patches
    }
}
