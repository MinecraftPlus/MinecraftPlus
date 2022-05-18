package org.minecraftplus.gradle.tasks.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class AbstractGitRepositoryTask extends DefaultTask {

    @TaskAction
    def exec() {
        throw new UnsupportedOperationException("Not implemented task!")
    }

    boolean isClean(Git git) {
        return git.status().call().isClean()
    }

    boolean isCheckedoutOn(Git git, String branchName) {
        git.getRepository().getBranch().equals(branchName)
    }

    Ref checkoutBranch(Git git, String branchName) {
        return checkoutBranch(git, branchName, false)
    }

    Ref checkoutBranch(Git git, String branchName, boolean force) {
        logger.lifecycle("Checking out branch '{}'", branchName)
        try {
            def checkout = git.checkout()
                    .setName(branchName)
                    .setForced(force)
                    .call()
            return checkout
        } catch (Exception e) {
            throw new Exception("Cannot checkout branch " + branchName, e)
        }
    }
}
