package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
/**
 * Initializes project repository
 */
class InitRepository extends AbstractGitRepositoryTask {

    @InputDirectory File target
    @Input String branchName = "master"
    @Input String commitMessage = "Initial commit"

    @Optional @Input String[] additionalBranches = []

    @TaskAction
    def exec() {
        logger.lifecycle("Initializing repository in '{}'", target)

        // Check if repository is already created
        try (Git git = Git.open(target)) {
            def repository = git.getRepository()
            logger.lifecycle(" - repository exist", target)

            //Check if repository already contains all needed branches
            def branches = git.branchList().call().collect { ref ->
                ref.name.split('/').last()
            }
            logger.lifecycle(" - contains branches {}", branches)

            def needed = additionalBranches.plus(branchName).toUnique().toList()
            def missing = []

            logger.lifecycle("Checking branches needed: {}", needed)
            def allPresent = needed.every { bname ->
                if (branches.contains(bname)) {
                    return true
                } else {
                    missing.add(bname)
                }
            }

            // If there is any branch in repository and any needed branch is missing throw an exception
            //  because it can be fixed only manually yet
            if (!branches.isEmpty() && !allPresent) {
                throw new RefNotFoundException("A branch is missing in repository: ${missing}")
            }

            // Try to stage all files and create initial commit
            try {
                git.add()
                        .addFilepattern(".")
                        .call()
                logger.lifecycle("Staged all files into repository")

                git.commit()
                        .setAll(true)
                        .setMessage(commitMessage)
                        .call()
                logger.lifecycle("Committed changes to repository")

                // Create additional branches for other purposes
                additionalBranches.each { bName ->
                    try {
                        def bRef = git.branchCreate().setName(bName).call()
                        logger.lifecycle("Created branch: {}", bRef.name)
                    } catch (RefAlreadyExistsException e) {}
                }
            } finally {
                checkoutBranch(git, branchName, true)
            }

        } catch (RepositoryNotFoundException e) {
            throw new RepositoryNotFoundException("Cannot open repository in: ${target}")
        }
    }
}
