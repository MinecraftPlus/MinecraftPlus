package org.minecraftplus.gradle.tasks.assets

import net.minecraftforge.mcpconfig.tasks.SingleFileOutput
import org.minecraftplus.gradle.tasks.Utils
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageAssets extends SingleFileOutput {

    @InputFile json
    @InputDirectory input

    @TaskAction
    def exec() {
        Utils.init()

        def dl = json.json.assetIndex
        def index = new File(input, 'indexes/' + dl.id + '.json')
        if (!index.exists())
            throw new GradleException("Cannot find index '" + dl +"'")
        if (index.sha1 != dl.sha1)
            throw new GradleException("Index SHA does not match '" + index.sha1  +"' expected '" + dl + "'")

        new ZipOutputStream(dest.newOutputStream()).withCloseable { output ->
            index.json.objects.each { asset ->
                def name = asset.key
                def key = asset.value.hash.take(2) + '/' + asset.value.hash

                def file = new File(input, 'objects/' + key)
                if (!file.exists() || !file.isFile())
                    throw new GradleException("Cannot find asset '" + key + "'")

                def filePath = file.toPath()
                def fileAttr = Files.readAttributes(filePath, BasicFileAttributes.class);

                def entry = new ZipEntry('assets/' + name)
                entry.lastModifiedTime = fileAttr.lastModifiedTime()
                output.putNextEntry(entry)
                def buffer = new byte[file.size()]
                file.withInputStream {
                    output.write(buffer, 0, it.read(buffer))
                }
                output.closeEntry()
            }
        }
    }
}