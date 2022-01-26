package org.minecraftplus.gradle.tasks.jars

import net.minecraftforge.mcpconfig.tasks.ToolJarExec
import net.minecraftforge.mcpconfig.tasks.Utils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

class MergeJar extends ToolJarExec {
    @InputFile File client
    @InputFile File server
    @OutputFile File dest
    @Optional @Input String annotations
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'client': client.absolutePath,
            'server': server.absolutePath,
            'output': dest.absolutePath,
            'annotations': annotations
        ]))
    }
}
