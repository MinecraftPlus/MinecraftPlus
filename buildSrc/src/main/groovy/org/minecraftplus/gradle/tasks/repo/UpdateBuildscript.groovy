package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

class UpdateBuildscript extends AbstractGitRepositoryTask {

    @InputDirectory File target

    @Input String developmentBranch = "development"
    @Input String buildscriptBranch = "buildscript"

    @Input String commitMessage = "Update buildscript"
    @Input String updateMessage = "Apply buildscript update"
    @Input String stagePattern = "."

    @Input MergeStrategy mergeStrategy = MergeStrategy.RECURSIVE
    @Input ContentMergeStrategy contentStrategy = ContentMergeStrategy.CONFLICT
    @Input MergeCommand.FastForwardMode fastForward = MergeCommand.FastForwardMode.NO_FF

    @TaskAction
    def exec() {
        logger.lifecycle("Staging changes into repository '{}'", target)

        try (Git git = Git.open(target)) {
            logger.lifecycle(" - repository opened", target)

            def developmentRef = null
            def buildscriptRef = null

            // Try to update buildscript branch
            try {
                buildscriptRef = checkoutBranch(git, buildscriptBranch)

                def addCommand = git.add()
                        .addFilepattern(stagePattern)
                        .call()
                logger.lifecycle("Staged files into repository with stage pattern: {}", stagePattern)

                def commitCommand = git.commit()
                        .setAllowEmpty(false)
                        .setMessage(commitMessage)

                def commitResult = commitCommand.call()
                logger.lifecycle("Committed changes into repository as '{}'", commitResult.getFullMessage())

                // Update buildscript branch ref
                buildscriptRef = git.getRepository().resolve(commitResult.getId().getName())
            } catch (EmptyCommitException e) {
                logger.lifecycle(" - no buildscript changes")
            } catch (Exception e) {
                throw e
            } finally {
                developmentRef = checkoutBranch(git, developmentBranch, true)
            }

            // End if there are no changes
            if (buildscriptRef == null) {
                return
            }

            // Try to merge buildscript changes into development
            try {
                def state = git.getRepository().getRepositoryState();
                logger.lifecycle("Repository in state: {}", state.toString())
                switch (state) {
                    case RepositoryState.SAFE: {
                        logger.lifecycle("Merging changes from branch '{}'", buildscriptRef.name)
                        logger.lifecycle(" - using merge/content strategy: {}/{}",
                                mergeStrategy.getName(), contentStrategy.toString())

                        def merge = git.merge()
                                .include(buildscriptRef)
                                .setMessage(updateMessage)
                                .setStrategy(mergeStrategy)
                                .setContentMergeStrategy(contentStrategy)
                                .setFastForward(fastForward)
                                .setCommit(false)
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
                                    logger.lifecycle("Branch '{}' has already merged into branch '{}'", buildscriptRef.name, developmentRef.name)
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
            } catch (Exception e) {
                throw e
            } finally {
                // Force checkout back do development branch
                developmentRef = checkoutBranch(git, developmentBranch, true)
            }
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${target}'")
        } catch (Exception e) {
            throw e
        }
    }
}
