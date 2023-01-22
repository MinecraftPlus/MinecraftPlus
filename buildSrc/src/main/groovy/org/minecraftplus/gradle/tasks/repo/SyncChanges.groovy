package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.URIish
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Fetches changes from upstream and merges it into synchronization branch.
 * Then those changes are merged into development branch.
 */
class SyncChanges extends AbstractGitRepositoryTask {

    @InputDirectory File target

    @Input String synchronizationBranch = "synchronization"
    @Input String developmentBranch = "development"

    @Input String remoteName
    @Input String remoteUrl
    @Input String remoteBranch = "master"

    @Input String commitMessage = "Synchronisation commit"
    @Input String updateMessage = "Apply synchronisation changes"

    @Optional @Input Boolean dropHistory = true

    @Internal
    File lockfile

    @TaskAction
    def exec() {
        lockfile = new File(target, ".synchronized")

        try (Git git = Git.open(target)) {
            def repository = git.getRepository()

            // Add remote if not exist
            def remotes = git.remoteList().call()
            if (remotes.stream().map(RemoteConfig::getName).noneMatch(r -> r == remoteName)) {
                git.remoteAdd()
                        .setName(remoteName)
                        .setUri(new URIish(remoteUrl))
                        .call()
                logger.lifecycle("Added remote '{}' from '{}'", remoteName, remoteUrl)
            }

            // Check if remote has requested branch
            def remoteRefs = git.lsRemote()
                    .setRemote(remoteName)
                    .call()
            def remoteRef = remoteRefs.stream()
                    .filter(ref -> ref.getName() == "refs/heads/" + remoteBranch)
                    .findFirst().orElse(null)
            if (remoteRef == null) {
                throw new GradleException("Remote repository '${remoteUrl}' does not contain branch '${remoteBranch}'")
            }

            // Configure repository
            def config = repository.getConfig()
            // to only fetch 'master' from from remote branches
            config.setString("remote", remoteName, "fetch",
                    "+refs/heads/${remoteBranch}:refs/remotes/${remoteName}/${remoteBranch}")
            config.save()

            // Fetch from remote
            def fetch = git.fetch()
                    .setRemote(remoteName)
                    .setTagOpt(TagOpt.NO_TAGS)
                    .call()
            logger.lifecycle("Fetched '{}' branch from remote '{}'", remoteBranch, remoteName)


            def synchronizationRef = repository.findRef(synchronizationBranch)
            def developmentRef = repository.findRef(developmentBranch)

            // Try to merge fetched changes into synchronization branch
            try {
                synchronizationRef = checkoutBranch(git, synchronizationBranch)

                def state = repository.getRepositoryState();
                logger.lifecycle("Repository in state: {}", state.toString())
                switch (state) {
                    case RepositoryState.SAFE: {
                        def inSync = lockfile?.exists()
                        logger.lifecycle("Were synchronized: {}", inSync)

                        // On first repository sync we need 'ours' strategy when syncing
                        def mergeStrategy = inSync
                                ? MergeStrategy.RECURSIVE : MergeStrategy.OURS
                        def contentStrategy = inSync
                                ? ContentMergeStrategy.CONFLICT : ContentMergeStrategy.OURS

                        logger.lifecycle("Merging changes of '{}' from remote '{}' to local '{}'",
                                remoteRef.name, remoteName, synchronizationRef.name)
                        logger.lifecycle(" - using merge/content strategy: {}/{}",
                                mergeStrategy.getName(), contentStrategy.toString())

                        def merge = git.merge()
                                .include(remoteRef)
                                .setMessage(commitMessage)
                                .setStrategy(mergeStrategy)
                                .setContentMergeStrategy(contentStrategy)
                                .setFastForward(MergeCommand.FastForwardMode.FF)
                                .setSquash(dropHistory)
                                .setCommit(false) // need no commit to add lock file
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
                                    logger.lifecycle("Remote branch '{}' has already merged in local branch '{}'", remoteRef.name, synchronizationBranch)
                                    break
                                default:
                                    break
                            }
                        }

                        // no break! If successfully then fallthrough to next case step
                    }
                    case RepositoryState.MERGING_RESOLVED: {
                        // Create a lock file to determine if your repositories have already been synced
                        if (!lockfile.exists()) {
                            createLockFile()
                            git.add().addFilepattern(lockfile.getName()).call()
                            logger.lifecycle("Created '{}' lock file", lockfile.getName())
                        }

                        def commitCommand = git.commit()
                                .setMessage(commitMessage)
                                .setAllowEmpty(false)
                        try {
                            def result = commitCommand.call()
                            logger.lifecycle("Committed changes as '{}'", result.getFullMessage())
                        } catch (EmptyCommitException e) {
                            logger.lifecycle("There are no new changes to commit")
                        }
                        break;
                    }
                    case RepositoryState.MERGING: {
                        throw new GradleException("Resolve merge conflicts in repository before resume syncing!")
                    }
                    default:
                        throw new IllegalStateException("Cannot be here, ups!")
                }
            } finally {
                synchronizationRef = repository.findRef(synchronizationBranch)
                developmentRef = checkoutBranch(git, developmentBranch, true)
            }


            // Try to merge changes into development branch
            try {
                def state = repository.getRepositoryState();
                logger.lifecycle("Repository in state: {}", state.toString())
                switch (state) {
                    case RepositoryState.SAFE: {
                        logger.lifecycle("Merging changes from '{}' into '{}'", synchronizationRef.name, developmentRef.name)

                        def merge = git.merge()
                                .include(synchronizationRef)
                                .setMessage(commitMessage)
                                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                                .setCommit(false) // need no commit to add lock file
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
                                    logger.lifecycle("Branch '{}' has already merged in '{}'", synchronizationRef.name, developmentRef.name)
                                    return
                                default:
                                    break
                            }
                        }

                        // no break! If successfully then fallthrough to next case step
                    }
                    case RepositoryState.MERGING_RESOLVED: {
                        def commitCommand = git.commit()
                                .setMessage(commitMessage)
                                .setAllowEmpty(false)
                        try {
                            def result = commitCommand.call()
                            logger.lifecycle("Committed changes as '{}'", result.getFullMessage())
                        } catch (EmptyCommitException e) {
                            logger.lifecycle("There are no new changes")
                        }
                        break;
                    }
                    case RepositoryState.MERGING: {
                        throw new GradleException("Resolve merge conflicts in repository before resume syncing!")
                    }
                    default:
                        throw new IllegalStateException("Cannot be here, ups!")
                }
            } finally {
                developmentRef = checkoutBranch(git, developmentBranch, true)
            }
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${target}'")
        } catch (Exception e) {
            throw e
        }
    }

    def createLockFile() {
        lockfile.createNewFile()
        lockfile.append("DON'T DELETE THIS FILE!!!)\r\n\r\n")
        lockfile.append("This file is used to check if distro repository is in synchronization with upstream branch\r\n\r\n")
        lockfile.append("DON'T DELETE THIS FILE!!!)\r\n")
    }
}
