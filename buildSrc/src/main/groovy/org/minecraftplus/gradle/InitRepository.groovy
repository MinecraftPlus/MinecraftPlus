package org.minecraftplus.gradle

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
/**
 * Initializes project repository
 */
class InitRepository extends DefaultTask {

    @InputDirectory File target
    @Input String branchName = "master"

    @TaskAction
    def exec() {
        logger.lifecycle("Initializing repository in '{}'", target)

        // Check if repository is already created
        try (Git git = Git.open(target)) {
            logger.lifecycle(" - repository exist")
            return // Nothing to do
        } catch (RepositoryNotFoundException e) {}

        logger.lifecycle(" - repository not exist, creating fresh one", target)
        try (Git git = Git.init()
                .setDirectory(target)
                .setInitialBranch(branchName)
                .call()
        ) {
            logger.lifecycle("Created repository")

            git.add()
                    .addFilepattern(".")
                    .call()
            logger.lifecycle("Staged all files into repository")

            git.commit()
                    .setAll(true)
                    .setMessage("Initial commit")
                    .call()
            logger.lifecycle("Committed changes to repository")
        }
    }
}
