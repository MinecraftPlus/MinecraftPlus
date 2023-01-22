package org.minecraftplus.gradle.tasks.maven


import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.minecraftplus.gradle.tasks.Utils

class DownloadLibraries extends DefaultTask {
    @InputFile json
    @Input config

    @Optional @Input String remote = 'https://maven.minecraftplus.org/'
    @Optional @Input Boolean quiet = false

    @Input destination // Directory where downloaded libraries will be stored

    @TaskAction
    def exec() {
        Utils.init()

        json.json.libraries.each { lib ->
            def artifacts = (lib.downloads.artifact == null ? [] : [lib.downloads.artifact]) + lib.downloads.get('classifiers', [:]).values()
            artifacts.each{ art ->
                download(art.url, new File(destination as File, art.path))
            }
        }

        config.distributions.each { name, config  ->
            config.libraries?.each { artifact ->
                download(
                        remote + artifact.toMavenPath() as String,
                        new File(destination as File, artifact.toMavenPath() as String)
                )
            }
        }
    }

    def download(def path, def target) {
        def ret = new DownloadAction(project, this)
        ret.overwrite(false)
        ret.useETag('all')
        ret.src path
        ret.dest target
        ret.quiet(quiet)
        ret.execute()
    }
}
