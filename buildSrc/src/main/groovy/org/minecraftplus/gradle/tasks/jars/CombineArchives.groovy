package org.minecraftplus.gradle.tasks.jars

import net.minecraftforge.mcpconfig.tasks.SingleFileOutput
import net.minecraftforge.mcpconfig.tasks.Utils
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Combines two JARs into one.
 */
class CombineArchives extends SingleFileOutput {

    @InputFile File first
    @InputFile File second

    @TaskAction
    def exec() {
        new ZipOutputStream(dest.newOutputStream()).withCloseable { out ->
            new ZipInputStream(first.newInputStream()).withCloseable { zis ->
                for (def entry = zis.nextEntry; entry != null; entry = zis.nextEntry) {
                    def oentry = new ZipEntry(entry.name)
                    oentry.lastModifiedTime = entry.lastModifiedTime
                    out.putNextEntry(oentry)
                    out << zis
                }
            }
            new ZipInputStream(second.newInputStream()).withCloseable { zis ->
                for (def entry = zis.nextEntry; entry != null; entry = zis.nextEntry) {
                    def oentry = new ZipEntry(entry.name)
                    oentry.lastModifiedTime = entry.lastModifiedTime
                    try {
                        out.putNextEntry(oentry)
                        out << zis
                    } catch (ZipException e) {
                        logger.trace(e.getLocalizedMessage())
                    }
                }
            }
        }
    }
}
