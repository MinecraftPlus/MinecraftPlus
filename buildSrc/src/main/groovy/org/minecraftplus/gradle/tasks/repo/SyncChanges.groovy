package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
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
 * Fetches changes from upstream and merges it
 */
class SyncChanges extends DefaultTask {

    @InputDirectory File target
    @Input String remoteName
    @Input String remoteUrl
    @Input String localBranch = "synchronisation"
    @Input String remoteBranch = "master"
    @Input String commitMessage = "Synchronisation commit"

    @Internal
    File lockfile

    @TaskAction
    def exec() {
        lockfile = new File(target, ".synchronized")

        try (Git git = Git.open(target)) {
            def repository = git.getRepository()

            // Checkout local branch
            def checkout = git.checkout()
                    .setName(localBranch)
                    .call()
            logger.lifecycle("Checked out local branch '{}'", checkout.name)

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
            // to to only fetch 'master' from from remote branches
            config.setString("remote", remoteName, "fetch",
                    "+refs/heads/${remoteBranch}:refs/remotes/${remoteName}/${remoteBranch}")
//            // merge driver to process .gitattributes
//            if (!config.getBoolean("merge", "ours", "driver", false)) {
//                config.setBoolean("merge", "ours", "driver", true)
//            }
            config.save()

            // Fetch from remote
            def fetch = git.fetch()
                    .setRemote(remoteName)
                    .call()
            logger.lifecycle("Fetched '{}' branch from remote '{}'", remoteBranch, remoteName)

            def inSync = lockfile?.exists()
            logger.lifecycle("Were synchronized: {}", inSync)

            def state = repository.getRepositoryState();
            logger.lifecycle("Repository in state: {}", state.toString())
            switch(state) {
                case RepositoryState.SAFE: {
                    // On first repository sync we need 'ours' strategy when syncing
                    def mergeStrategy = inSync
                            ? MergeStrategy.RECURSIVE : MergeStrategy.OURS
                    def contentStrategy = inSync
                            ? ContentMergeStrategy.CONFLICT : ContentMergeStrategy.OURS

                    logger.lifecycle("Merging changes in '{}' from remote '{}' to local '{}'",
                            remoteBranch, remoteName, localBranch)
                    logger.lifecycle(" - using merge/content strategy: {}/{}",
                            mergeStrategy.getName(), contentStrategy.toString())

                    def merge = git.merge()
                            .include(remoteRef)
                            .setMessage(commitMessage)
                            .setStrategy(mergeStrategy)
                            .setContentMergeStrategy(contentStrategy)
                            .setCommit(false) // need no commit to add lock file
                            .call()
                    def status = merge.getMergeStatus()
                    logger.lifecycle("Merge status: {}", status)

//                    TODO: Maybe replace merge with pull?
//                    def pull = git.pull()
//                            .setRemote(remoteName)
//                            .setRemoteBranchName(remoteBranch)
//                            .setStrategy(strategy)
//                            .setContentMergeStrategy(contentStrategy)
//                            .call()
//                    logger.lifecycle("Pull fetch status: {}", pull.getFetchResult())
//                    logger.lifecycle("Pull merge status: {}", pull.getMergeResult())

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
                                logger.lifecycle("Remote branch '{}' has already merged in local branch '{}'", remoteBranch, localBranch)
                                return
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

                    // Commit results
                    def result = git.commit()
                            .call()
                    logger.lifecycle("Committed changes as '{}'", result.getFullMessage())
                    break;
                }
                case RepositoryState.MERGING: {
                    throw new GradleException("Resolve merge conflicts in repository before resume syncing!")
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

    def createLockFile() {
        lockfile.createNewFile()
        lockfile.append("DON'T DELETE THIS FILE!!!)\r\n\r\n")
        lockfile.append("This file is used to check if distro repository is in synchronization with upstream branch\r\n\r\n")
        lockfile.append("DON'T DELETE THIS FILE!!!)\r\n")
    }
}
