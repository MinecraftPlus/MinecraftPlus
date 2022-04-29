package org.minecraftplus.gradle.tasks.meta

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.minecraftplus.gradle.tasks.Utils

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class GenerateVersionInfo extends DefaultTask {

    @Input String id
    @Input String release_name
    @Input String release_target

    @Input Integer world_version
    @Input Integer protocol_version
    @Input Integer pack_version

    @Optional @Input ZonedDateTime build_time = null
    @Optional @Input Boolean stable = false

    @OutputFile File output

    @TaskAction
    def exec() {
        Utils.init()

        // Generate JSON
        def json = new JsonBuilder()
        json(
                id: id,
                name: release_name,
                release_target: release_target,
                world_version: world_version,
                protocol_version: protocol_version,
                pack_version: pack_version,
                build_time: (build_time ? build_time : ZonedDateTime.now()).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                stable: stable
        )

        output.text = JsonOutput.prettyPrint(json.toString())
    }
}
