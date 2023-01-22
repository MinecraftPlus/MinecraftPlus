package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.TransportException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

class CheckoutBranch extends DefaultTask {

    @InputDirectory File target

    @Input String branchName

    @TaskAction
    def exec() {
        try (Git git = Git.open(target)) {
            def checkout = git.checkout()
                    .setName(branchName)
                    .call()
            logger.lifecycle("Checked out local branch '{}'", checkout.name)
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${target}'")
        } catch (Exception e) {
            throw e
        }
    }
}
