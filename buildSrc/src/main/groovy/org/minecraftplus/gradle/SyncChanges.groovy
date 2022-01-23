package org.minecraftplus.gradle

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.util.function.Function

/**
 * Fetches changes from upstream and merges it
 */
class SyncChanges extends DefaultTask {

    @InputDirectory def File target
    @Optional @InputFile def File freshinit
    @Input String remoteName
    @Input String remoteUrl
    @Input String localBranch = "master"
    @Input String remoteBranch = "master"
    @Input String message = "Synchronisation"

    @TaskAction
    def exec() {
        try (Git git = Git.open(target)) {

            def remotes = git.remoteList().call()
            if (remotes.stream().map(RemoteConfig::getName).noneMatch(r -> r == remoteName)) {
                git.remoteAdd()
                        .setName(remoteName)
                        .setUri(new URIish(remoteUrl))
                        .call()
                logger.lifecycle("Adding remote '{}' from '{}'", remoteName, remoteUrl)
            }

            git.fetch()
                    .setRemote(remoteName)
                    .setInitialBranch(remoteBranch)
                    .call()
            logger.lifecycle("Fetched '{}' branch from remote '{}'", remoteBranch, remoteName)

            git.checkout()
                    .setName(localBranch)
                    .call()
            logger.lifecycle("Checked out local branch '{}'", localBranch)

            ObjectId localRef = git.getRepository().resolve(localBranch)
            logger.lifecycle("     local '{}'", localRef.getName())

            ObjectId upstreamRef = git.getRepository()
                    .resolve(remoteName + "/" + remoteBranch)
            logger.lifecycle("  upstream '{}'", upstreamRef.getName())

            RevWalk revWalk = new RevWalk(git.getRepository())
            for (RevCommit parent : revWalk.parseCommit(localRef).getParents()) {
                if (upstreamRef.equals(parent)) {
                    logger.lifecycle("Remote branch '{}' has already merged in local branch '{}'", remoteBranch, localBranch)
                    return
                }
            }

            // On fresh repositories we need ours when syncing, else recursive
            def strategy = freshinit?.exists()
                    ? MergeStrategy.OURS : MergeStrategy.RECURSIVE

//            logger.lifecycle("Fresh file {}", freshinit?.toString())
//            logger.lifecycle("Fresh exists {}", freshinit?.exists())
//            logger.lifecycle("Fresh size {}", freshinit?.size())
//            logger.lifecycle("Strategy  {}", strategy)

            git.merge().include(upstreamRef)
                    .setMessage(message)
                    .setStrategy(strategy)
                    .setContentMergeStrategy(ContentMergeStrategy.OURS)
//                    .setCommit(false)
                    .call()
            logger.lifecycle("Merged changes from '{}' remote '{}' with strategy '{}'",
                    remoteBranch, remoteName, strategy.getName())

            if (freshinit?.exists()) {
                freshinit.delete()
//            git.add()
//                    .addFilepattern(freshinit.getName())
//                    .call()
                logger.lifecycle("Removed '{}' lock file", freshinit.getName())
            }

//            git.commit()
//                    .setMessage(message)
//                    .call()
//            logger.lifecycle("Committed changes '{}'", message)
        } catch (Exception e) {
            throw e
        }
    }
}
