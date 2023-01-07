package org.minecraftplus.gradle.tasks.smart

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.minecraftplus.gradle.tasks.Utils

class CreateSmartConfig extends DefaultTask {

    @Input String branch
    @Optional @Input String lastcommit = null

    @Optional @Input String include = ["master"]
    @Optional @Input String ignore = []

    @Internal File output

    @TaskAction
    def exec() {
        Utils.init()

        // Generate JSON
        def json = new JsonBuilder()
        json(
            branch: branch,
            lastcommit: lastcommit,
            include: include,
            ignore: ignore,
            maxCount: 50
        )

        output.text = JsonOutput.prettyPrint(json.toString())
    }
}
