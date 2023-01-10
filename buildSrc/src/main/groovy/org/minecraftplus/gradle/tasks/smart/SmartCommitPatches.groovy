package org.minecraftplus.gradle.tasks.smart

import groovy.json.JsonBuilder
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

class SmartCommitPatches extends DefaultTask {

    @Input String branchName
    @Input String commitId
    @Input String commitMessage

    @Input gitRepo //TODO Annotating this as @InputDirectory causes wrong task dependency resolution with makeTask

    @Input
    Map<File, File> patchSets = new HashMap<>()

    def patchSet(File patches, File dest) {
        patchSets.put(patches, dest)

        // Configure output to proper up-to-date checking
        inputs.dir(patches)
        outputs.dir(dest)
    }

    @TaskAction
    void doTask() {

        try (Git git = Git.open(gitRepo)) {
            def repository = git.getRepository()

            // Check repository state - we need to be in safe checked-out state
            def state = repository.getRepositoryState();
            logger.lifecycle("Repository in state: {}", state.toString())
            switch (state) {
                case RepositoryState.SAFE: {
                    break
                }
                default:
                    throw new IllegalStateException("Repository need be in safe state!")
            }

            // Check working tree status
            //TODO: Add checks to verify that worktree can be safely edited, files can be staged, changes commited etc.
//            def status = git.status()
//                    .call()
//            logger.lifecycle("Status: {}", status.toString())
//            logger.lifecycle("Status isClean: {}", status.isClean())
//            logger.lifecycle("Status untracked: {}", status.untracked)
//            logger.lifecycle("Status added: {}", status.added)
//            logger.lifecycle("Status removed: {}", status.removed)
//            logger.lifecycle("Status modified: {}", status.modified)
//            logger.lifecycle("Status missing: {}", status.missing)
//            logger.lifecycle("Status uncommittedChanges: {}", status.uncommittedChanges)
//
//
//            def diff = git.diff().setCached(true).call()
//            diff.each {
//                logger.lifecycle("Diff: {}", it.toString())
//            }

            def addCommand = git.add()
            def addCommand2 = git.add().setUpdate(true)

            patchSets.each { source, dest ->
                // Need to remove old patches first
                FileUtils.cleanDirectory(dest)
                logger.lifecycle("Cleaned old patches in '{}'", dest)

                // Copy smart patches to destination directory
                FileUtils.copyDirectory(source, dest);
                logger.lifecycle("Copied patches from '{}' to '{}'", source, dest)

                addCommand.addFilepattern(gitRepo.relativePath(dest))
                addCommand2.addFilepattern(gitRepo.relativePath(dest))
                logger.trace("Added stage file-pattern: '{}'", gitRepo.relativePath(dest))
            }

            def addResult = addCommand.call()
            def addResult2 = addCommand2.call()
            logger.trace("Staged all files into repository")

            // Prepare commit message
            def message = "[SP] ${commitMessage}"
            logger.trace("Commit message: '{}'", message)

            def commit = git.commit()
                    .setAllowEmpty(false)
                    .setMessage(message)
                    .call()
            logger.lifecycle("Successfully committed changes to repository: '{} {}'", commit.name(), commit.getShortMessage())
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }
    }
}
