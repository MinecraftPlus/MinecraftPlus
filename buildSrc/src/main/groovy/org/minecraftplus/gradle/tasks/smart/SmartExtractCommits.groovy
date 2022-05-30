package org.minecraftplus.gradle.tasks.smart

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.errors.StopWalkException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class SmartExtractCommits extends DefaultTask {

    @InputFile File smartconfig
    @InputDirectory File gitrepo

    @OutputFile File output

    @TaskAction
    void doTask() {

        if (!smartconfig.exists()) {
            throw new GradleException("Cannot work without configuration file!")
        }
        def config = smartconfig.json
        logger.lifecycle("Configuration:   {}/{}", config.branch, config.lastcommit, config.toString())

        def commits = []

        // Load previously generated list if exist
        if (output.exists()) {
            def json = output.json
            logger.lifecycle("Smartlist found: {}/{}", json.info.branch, json.info.latest, json.toString())

            // Check process consistency - commit id stored in config must match latest
            if (config.lastcommit != null && config.lastcommit != json.info.latest) {
                throw new GradleException("Inconsistent process!!! Commit ID stored in config does not match latest stored in list!")
            }

            // Load commits
            commits = json.commits
            if (!commits.isEmpty()) {
                logger.lifecycle("Loaded {} commits:", commits.size())
                commits.each {
                    logger.lifecycle(" # {} {}", it.id, it.message)
                }
            }
        }

        // Open git repository and scan
        try (Git git = Git.open(gitrepo)) {
            def repository = git.getRepository()

            // Check repository state - we need to be in safe checked-out state
            def state = repository.getRepositoryState();
            logger.lifecycle("Repository in state: {}", state.toString())
            switch (state) {
                case RepositoryState.SAFE: {
                    break
                }
                default:
                    throw new IllegalStateException("Cannot be here, ups!")
            }

            // Check actal branch - we need be in branch from smart config
            def actualBranch = repository.getBranch()
            logger.lifecycle("Actual branch: {}", actualBranch)
            if (actualBranch != config.branch) {
                throw new GradleException("Target repository need be checked-out on branch " + config.branch)
            }

            // Prepare git log command to get commit list
            def branchId = repository.resolve(actualBranch);
            def logCommand = git.log()
                .add(branchId)
                .setRevFilter(new RevFilter() { // Find any commit that have parent
                    @Override
                    boolean include(RevWalk walker, RevCommit commit) throws StopWalkException, MissingObjectException, IncorrectObjectTypeException, IOException {
                        logger.trace("Commit to include: {} {} ", commit.name, commit.shortMessage)

                        if (commit.getParentCount() == 0) {
                            logger.trace(" - initial commit")
                            return false // no initial commits
                        }
                        else if (commit.getParentCount() == 1) {
                            logger.trace(" - normal parentness")
                            logger.trace(" - testing commit: {}", commit.name)

                            def branches = git.branchList().setContains(commit.name).call().collect { ref ->
                                ref.name.split('/')[2..-1].join('/') // skip /ref/heads
                            }
                            logger.trace("  - branches containing commit: {}", branches)

                            def drop = branches.any { bName ->
                                logger.trace("   - testing branch name: {}", bName)
                                def ignore = config.ignore.any { String ign ->
                                    return bName.endsWith(ign)
                                }
                                if (ignore) {
                                    logger.trace("    - in ignored branch!")
                                }
                                return ignore
                            }

                            logger.trace(" - include commit: {}", !drop)
                            return !drop
                        } else {
                            def parents = commit.getParents()
                            logger.trace(" - parents: {}", parents.collect { it.name })

                            def drop = parents.any { parent ->
                                logger.trace("  - testing parent: {}", parent.name)

                                def branches = git.branchList().setContains(parent.name).call().collect { ref ->
                                    ref.name.split('/')[2..-1].join('/') // skip /ref/heads
                                }
                                logger.trace("    - branches containing commit: {}", branches)

                                def include = branches.any { bName ->
                                    logger.trace("     - testing branch name: {}", bName)
                                    def include = config.include.any { String incl ->
                                        return bName.endsWith(incl)
                                    }
                                    if (include) {
                                        logger.trace("      - in included branch!")
                                    }
                                    return include
                                }

                                def drop = branches.any { bName ->
                                    logger.trace("     - testing branch name: {}", bName)
                                    def ignore = config.ignore.any { String ign ->
                                        return bName.endsWith(ign)
                                    }
                                    if (ignore) {
                                        logger.trace("      - in ignored branch!")
                                    }
                                    return ignore
                                }

                                logger.trace("     - include: {}", include)
                                logger.trace("     -    drop: {}", drop)
                                return drop || !include
                            }

                            logger.trace(" - include commit: {}", !drop)
                            return !drop
                        }
                    }

                    @Override
                    RevFilter clone() { return this }
                })

            logger.lifecycle("Finding commit list:")
            def headRef = repository.resolve(Constants.HEAD);
            if (config.lastcommit != null) {
                def startRef = repository.resolve(config.lastcommit as String)
                logger.lifecycle(" {} to {}[HEAD]", startRef.name, headRef.name)
                logCommand = logCommand.addRange(startRef, headRef)
            } else {
                logger.lifecycle(" start to {}[HEAD]", headRef.name)
            }

            // Fetch list of new commits from repository
            def revisions = logCommand.call().asCollection().toList().reverse()
            if (!revisions.isEmpty()) {
                logger.lifecycle("Found {} new commits:", revisions.size())
                revisions.each {
                    logger.lifecycle(" # {} {}", it.getId().name(), it.getShortMessage())
                }
            } else {
                logger.lifecycle("No new commits found.")
            }

            revisions.each { newcommit ->
                // Check if commit is already on list
                if (commits.any { c -> return c.id == newcommit.id.name }) {
                    throw new GradleException("Cannot add - commit '" + newcommit.id.name + "' is already on list!")
                }

                commits.add([
                    id:      newcommit.id.name,
                    message: newcommit.shortMessage
                ])
            }
        } catch (TransportException e) {
            throw new GradleException("Cannot open git repository in '${gitrepo}'")
        } catch (Exception e) {
            throw e
        }

        if (commits.isEmpty()) {
            logger.lifecycle("No commits? No gain.")
            return
        }

        // Generate smart list JSON
        def oldest = commits.first()
        def latest = commits.last()
        def json = new JsonBuilder()
        json(
            info: [branch: config.branch, oldest: oldest.id, latest: latest.id, count: commits.size],
            commits: commits
        )
        output.text = JsonOutput.prettyPrint(json.toString())

        // Update smart config last commit
        config.lastcommit = latest.id
        smartconfig.text = new JsonBuilder(config).toPrettyString()
    }
}
