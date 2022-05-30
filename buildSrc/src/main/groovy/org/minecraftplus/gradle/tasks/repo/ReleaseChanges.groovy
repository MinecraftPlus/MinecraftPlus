package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Captures changes from one branch and merges it on another within one repository
 */
class ReleaseChanges extends DefaultTask {

    @InputDirectory File target

    @Input String sourceBranch = "development"
    @Input String targetBranch = "master"

    @Input String message = "Capture commit"

    @TaskAction
    def exec() {

        try (Git git = Git.open(target)) {
            def repository = git.getRepository()

            // Check if repository contains both branches
            def branches = git.branchList().call()
            def sourceRef = branches.find { branch -> branch.getName().endsWith(sourceBranch) }
            def targetRef = branches.find { branch -> branch.getName().endsWith(targetBranch) }
            if (!sourceRef) {
                throw new IllegalStateException("Repository does not contain source branch '${sourceBranch}'")
            }
            if (!targetRef) {
                throw new IllegalStateException("Repository does not contain target branch '${targetBranch}'")
            }

            // Get repository state
            def state = repository.getRepositoryState();
            logger.lifecycle("Repository in state: {}", state.toString())
            switch(state) {
                case RepositoryState.SAFE: {
                    // Checkout local target branch
                    def checkout = git.checkout()
                            .setName(targetBranch)
                            .call()
                    logger.lifecycle("Checked out local branch '{}'", checkout.name)

                    // On first repository sync we need 'ours' strategy when syncing
                    def ours = false
                    def mergeStrategy = ours
                            ? MergeStrategy.OURS : MergeStrategy.RECURSIVE
                    def contentStrategy = ours
                            ? ContentMergeStrategy.OURS : ContentMergeStrategy.CONFLICT

                    logger.lifecycle("Merging changes from branch '{}' to branch '{}'",
                            sourceRef.name, targetRef.name)
                    logger.lifecycle(" - using merge/content strategy: {}/{}",
                            mergeStrategy.getName(), contentStrategy.toString())

                    def merge = git.merge()
                            .include(sourceRef)
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
                                throw new GradleException("Merge results in content conflicts [" +  merge.getConflicts().size() + "]")
                            default:
                                throw new GradleException("Merge fails! [" + status + "]")
                        }
                    } else {
                        switch (status) {
                            case MergeResult.MergeStatus.ALREADY_UP_TO_DATE:
                                logger.lifecycle("Branch '{}' has already merged into branch '{}'", sourceRef.name, targetRef.name)
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
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }
    }
}
