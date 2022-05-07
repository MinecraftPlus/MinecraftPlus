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
class InitRepository extends DefaultTask {

    @InputDirectory File target
    @Input String branchName = "master"
    @Input String commitMessage = "Initial commit"

    @Optional @Input String[] additionalBranches = []

    @TaskAction
    def exec() {
        logger.lifecycle("Initializing repository in '{}'", target)

        // Check if repository is already created
        try (Git git = Git.open(target)) {
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
                    return false
                }
            }

            // If any branch is missing throw an exception because it can be fixed only manually yet
            if (!allPresent) {
                throw new RefNotFoundException("A branch is missing in repository: ${missing}")
            }

            return // Nothing to do
        } catch (RepositoryNotFoundException e) {}

        logger.lifecycle(" - repository not exist, creating fresh one", target)
        try (Git git = Git.init()
                .setDirectory(target)
                .setInitialBranch(branchName)
                .call()
        ) {
            def repository = git.getRepository()

            logger.lifecycle("Created repository")

            git.add()
                    .addFilepattern(".")
                    .call()
            logger.lifecycle("Staged all files into repository")

            git.commit()
                    .setAll(true)
                    .setMessage(commitMessage)
                    .call()
            logger.lifecycle("Committed changes to repository")

            // Create additional branch for other purposes
            additionalBranches.each { bName ->
                try {
                    def bRef = git.branchCreate().setName(bName).call()
                    logger.lifecycle("Created additional branch: {}", bRef.name)
                } catch (RefAlreadyExistsException e) {}
            }
        }
    }
}
