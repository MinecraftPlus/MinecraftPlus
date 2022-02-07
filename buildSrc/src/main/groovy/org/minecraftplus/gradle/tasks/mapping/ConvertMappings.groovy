package org.minecraftplus.gradle.tasks.mapping

import net.minecraftforge.mcpconfig.tasks.SingleFileOutput
import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.srgutils.*

public class ConvertMappings extends SingleFileOutput {
    @InputFile File input

    @TaskAction
    def exec() {
        Utils.init()
        def mapping = IMappingFile.load(input)
                .filter();

        mapping.write(dest.toPath(), IMappingFile.Format.TSRG2, true)
    }
}
