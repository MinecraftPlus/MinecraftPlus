package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Captures changes from development branch and merges it on release branch
 */
class ReleaseChanges extends AbstractGitRepositoryTask {

    @InputDirectory File target

    @Input String developmentBranch = "development"
    @Input String releaseBranch = "master"

    @Input String message = "Release development changes"

    @TaskAction
    def exec() {

        try (Git git = Git.open(target)) {
            def repository = git.getRepository()

            // Check if repository contains both branches
            def branches = git.branchList().call()
            def developmentRef = branches.find { branch -> branch.getName().endsWith(developmentBranch) }
            def releaseRef = branches.find { branch -> branch.getName().endsWith(releaseBranch) }
            if (!developmentRef) {
                throw new IllegalStateException("Repository does not contain source branch '${developmentBranch}'")
            }
            if (!releaseRef) {
                throw new IllegalStateException("Repository does not contain target branch '${releaseBranch}'")
            }

            // Get repository state
            def state = repository.getRepositoryState();
            logger.lifecycle("Repository in state: {}", state.toString())

            try {
                switch (state) {
                    case RepositoryState.SAFE: {
                        // Checkout local target branch
                        def checkout = git.checkout()
                                .setName(releaseBranch)
                                .call()
                        logger.lifecycle("Checked out local branch '{}'", checkout.name)

                        // On first repository sync we need 'ours' strategy when syncing
                        def ours = false
                        def mergeStrategy = ours
                                ? MergeStrategy.OURS : MergeStrategy.RECURSIVE
                        def contentStrategy = ours
                                ? ContentMergeStrategy.OURS : ContentMergeStrategy.CONFLICT

                        logger.lifecycle("Merging changes from branch '{}' to branch '{}'",
                                developmentRef.name, releaseRef.name)
                        logger.lifecycle(" - using merge/content strategy: {}/{}",
                                mergeStrategy.getName(), contentStrategy.toString())

                        def merge = git.merge()
                                .include(developmentRef)
                                .setMessage(message)
                                .setStrategy(mergeStrategy)
                                .setContentMergeStrategy(contentStrategy)
                                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                                .setCommit(false) // need no commit to add lock fil
                                .call()
                        def status = merge.getMergeStatus()
                        logger.lifecycle("Merge status: {}", status)

                        if (!status.isSuccessful()) {
                            switch (status) {
                                case MergeResult.MergeStatus.CONFLICTING:
                                    throw new GradleException("Merge results in content conflicts [" + merge.getConflicts().size() + "]")
                                default:
                                    throw new GradleException("Merge fails! [" + status + "]")
                            }
                        } else {
                            switch (status) {
                                case MergeResult.MergeStatus.ALREADY_UP_TO_DATE:
                                    logger.lifecycle("Branch '{}' has already merged into branch '{}'", developmentRef.name, releaseRef.name)
                                    return
                                default:
                                    break
                            }
                        }

                        // no break! If successfully then fallthrough to next case step
                    }
                    case RepositoryState.MERGING_RESOLVED: {
                        // Commit results
                        def result = git.commit()
                                .call()
                        logger.lifecycle("Committed changes as '{}'", result.getFullMessage())
                        break;
                    }
                    case RepositoryState.MERGING: {
                        throw new GradleException("Resolve merge conflicts in repository before resume capturing!")
                    }
                    default:
                        throw new IllegalStateException("Cannot be here, ups!")
                }
            } finally {
                developmentRef = checkoutBranch(git, developmentBranch, true)
            }
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }
    }
}
