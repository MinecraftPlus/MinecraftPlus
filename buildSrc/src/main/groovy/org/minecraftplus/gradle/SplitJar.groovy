package org.minecraftplus.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
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
    @OutputFile sources
    @OutputFile resources

    @TaskAction
    def exec() {
        new ZipOutputStream(sources.newOutputStream()).withCloseable{ so ->
            new ZipOutputStream(resources.newOutputStream()).withCloseable{ ro ->
                new ZipInputStream(input.newInputStream()).withCloseable{ jin ->
                    for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                        def out = entry.name.endsWith(".class") ? so : ro
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
