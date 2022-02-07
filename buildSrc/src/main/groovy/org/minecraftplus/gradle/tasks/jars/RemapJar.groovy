package org.minecraftplus.gradle.tasks.jars

import net.minecraftforge.mcpconfig.tasks.JarExec
import net.minecraftforge.mcpconfig.tasks.ToolJarExec
import net.minecraftforge.mcpconfig.tasks.Utils;
import org.gradle.api.*
import org.gradle.api.tasks.*

class RemapJar extends ToolJarExec {
    @InputFile File mappings
    @InputFile File input
    @InputFile File dictionary
    @InputFile File libraries
    @OutputFile File dest
    @OutputFile @Optional File log = null
    
    @Override
    protected void preExec() {
        def logStream = log == null ? JarExec.NULL_OUTPUT : log.newOutputStream()
        standardOutput logStream
        errorOutput logStream
        setArgs(Utils.fillVariables(args, [
            'mappings': mappings.absolutePath,
            'input': input.absolutePath,
            'output': dest.absolutePath,
            'dictionary': dictionary.absolutePath,
            'libraries': libraries.absolutePath
        ]))
    }
}