package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.ContentMergeStrategy
import org.eclipse.jgit.merge.MergeStrategy
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction


class CheckRepositoryState extends AbstractGitRepositoryTask {

    @InputDirectory File target

    @Input boolean isClean = false
    @Input String[] containsBranch = []
    @Input String checkedOn = null

    @TaskAction
    def exec() {
        try (Git git = Git.open(target)) {

            // Check if repository is in clean state
            if (isClean) {
                logger.lifecycle("Repository need to be in clean state")
                def status = isClean(git)
                if (!status) {
                    throw new IllegalStateException("Repository is not in clean state!")
                } else {
                    logger.lifecycle(" - repository is clean")
                }
            }

            // Check if repository contains given branches
            if (containsBranch.size() > 0) {
                logger.lifecycle("Repository need to contain branches: '{}'", containsBranch)
                def branches = git.branchList().call().collect { ref ->
                    ref.name.split('/').last()
                }

                def needed = containsBranch.toUnique().toList()
                def missing = []

                def allPresent = needed.every { bname ->
                    if (branches.contains(bname)) {
                        logger.lifecycle(" - branch '{}' present", bname)
                        return true
                    } else {
                        logger.lifecycle(" - missing branch '{}'", bname)
                        missing.add(bname)
                        return false
                    }
                }

                if (allPresent) {
                    logger.lifecycle(" - all needed branches present")
                } else {
                    throw new IllegalStateException("A branch is missing in repository: ${missing}")
                }
            }

            if (checkedOn) {
                logger.lifecycle("Repository need to be checked-out on: '{}'", checkedOn)
                def isCheckedOut = isCheckedoutOn(git, checkedOn)
                if (isCheckedOut) {
                    logger.lifecycle(" - repository already checked-out on '{}'", checkedOn)
                } else {
                    throw new IllegalStateException("Repository is not checked-out on branch '" + checkedOn + "'")
                }
            }

        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }
    }
}
