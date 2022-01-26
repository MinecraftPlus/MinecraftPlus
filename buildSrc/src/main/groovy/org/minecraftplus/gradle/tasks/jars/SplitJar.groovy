package org.minecraftplus.gradle.tasks.jars

import net.minecraftforge.mcpconfig.tasks.Utils
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Filters input JAR to two jars. One containing code sources and one
 * containing resources (non .class files)
 */
public class SplitJar extends DefaultTask {
    @InputFile input
    @InputFile mappings
    @Input acceptable = []
    @OutputFile sources
    @OutputFile resources
    @OutputFile libraries

    @TaskAction
    def exec() {
        Utils.init()
        def classes = IMappingFile.load(mappings).classes.collect{ it.original + '.class' }.toSet()

        new ZipOutputStream(sources.newOutputStream()).withCloseable{ so ->
            new ZipOutputStream(resources.newOutputStream()).withCloseable{ ro ->
                new ZipOutputStream(libraries.newOutputStream()).withCloseable { lo ->
                    new ZipInputStream(input.newInputStream()).withCloseable { jin ->
                        for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                            def out = null
                            if (classes.contains(entry.name)) {
                                out = so
                            } else if (acceptable.stream().anyMatch(it -> entry.name.startsWith(it as String))) {
                                out = entry.name.endsWith(".class") ? so : ro
                            } else
                                out = lo // library

                            def oentry = new ZipEntry(entry.name)
                            oentry.lastModifiedTime = entry.lastModifiedTime
                            out.putNextEntry(oentry)
                            out << jin
                        }
                    }
                }
            }
        }
    }
}
