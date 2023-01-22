package org.minecraftplus.gradle.tasks.mapping

import net.minecraftforge.mcpconfig.tasks.SingleFileOutput
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.minecraftplus.gradle.tasks.Utils

class ConvertMappings extends SingleFileOutput {
    @InputFile File input

    @TaskAction
    def exec() {
        Utils.init()
        def mapping = IMappingFile.load(input)
                .filter();

        mapping.write(dest.toPath(), IMappingFile.Format.TSRG2, true)
    }
}
