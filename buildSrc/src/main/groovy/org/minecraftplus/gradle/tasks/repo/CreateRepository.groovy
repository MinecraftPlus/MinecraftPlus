package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Creates empty git repository
 */
class CreateRepository extends DefaultTask {

    @InputDirectory File target

    @TaskAction
    def exec() {
        logger.lifecycle("Creating repository in '{}'", target)

        // Check if repository is already created
        try (Git git = Git.open(target)) {
            logger.lifecycle(" - repository exist", target)

            //Check if repository already contains all needed branches
            def branches = git.branchList().call().collect { ref ->
                ref.name.split('/').last()
            }
            logger.lifecycle(" - contains branches {}", branches)

        } catch (RepositoryNotFoundException e) {}

        logger.lifecycle(" - repository not exist, creating fresh one", target)
        try (Git git = Git.init().setDirectory(target).call()
        ) {
            logger.lifecycle("Created repository")
        }
    }
}
