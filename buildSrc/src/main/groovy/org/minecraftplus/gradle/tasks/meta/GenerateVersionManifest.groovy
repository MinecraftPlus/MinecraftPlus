package org.minecraftplus.gradle.tasks.meta

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.minecraftplus.gradle.tasks.Utils

import java.security.MessageDigest

class GenerateVersionManifest extends DefaultTask {

    @Input String id
    @Input String inherit
    @Input String type
    @Optional @Input Date time = new Date()
    @Optional @Input Date releaseTime = new Date()
    @Optional @Input Integer complianceLevel = 0

    @Input
    Map<String, File> downloadFiles = new HashMap<>()

    def download(String name, File file) {
        downloadFiles.put(name, file)
    }

    @OutputDirectory File output

    @TaskAction
    def exec() {
        Utils.init()

        // Prepare downloads
        def downloads = [:]
        downloadFiles.each { name, file ->
            if (file.exists()) {
                downloads.put(name, [
                        sha1: sha1(file),
                        size: file.size()
                ])
            }
        }

        // Generate JSON
        def json = new JsonBuilder()
        json(
                id: id,
                inheritsFrom: inherit,
                time: time,
                releaseTime: releaseTime,
                type: type,
                downloads: downloads,
                complianceLevel: complianceLevel
        )

        new File(output, id + '.json').text = JsonOutput.prettyPrint(json.toString())
    }

    // See https://stackoverflow.com/a/43082685
    @Internal
    MessageDigest digest = MessageDigest.getInstance("SHA-1")

    def sha1(file) {
        digest.update(file.getBytes())
        return digest.digest().collect {
            String.format('%02x', it)
        }.join()
    }
}
