package org.minecraftplus.gradle.tasks.jars

import net.minecraftforge.mcpconfig.tasks.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageAssets extends DefaultTask {

    @InputFile json
    @InputDirectory input
    @OutputFile def dest

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

                output.putNextEntry(new ZipEntry(name))
                def buffer = new byte[file.size()]
                file.withInputStream {
                    output.write(buffer, 0, it.read(buffer))
                }
                output.closeEntry()
            }
        }
    }
}