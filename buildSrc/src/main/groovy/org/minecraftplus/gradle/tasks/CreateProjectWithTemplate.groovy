package org.minecraftplus.gradle.tasks

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.minecraftplus.gradle.tasks.Utils

import java.util.zip.ZipFile

class CreateProjectWithTemplate extends DefaultTask {
    @Input String distro
    @InputFile File template
    @Optional @InputFile File gitignore
    @Optional @InputFile File meta
    @Optional @InputFile File bundle
    @Input List<String> libraries = new ArrayList<>()
    @Input Map<String, String> replace = new HashMap<>()
    @Internal Set<File> directories = new HashSet<>()
    
    @OutputDirectory File dest
    
    def library(lib) {
        libraries.add(lib)
    }
    
    def libraryFile(lib) {
        libraries.add("files('" + lib.absolutePath.replace('\\', '/') + "')")
    }
    
    def replace(key, value) {
        replace.put(key, value)
    }
    
    def replaceFile(key, value) {
        if (value == null) {
            replace(key, null)
        } else {
            replace(key, "'" + value.absolutePath.replace('\\', '/') + "'")
            directories.add(value)
        }
    }
    
    @TaskAction
    protected void exec() {
        if (!dest.exists())
            dest.mkdirs()
        
        for (def dir : directories) {
            if (!dir.exists())
                dir.mkdirs()
        }

        // Create project build.gradle
        new File(dest, 'build.gradle').withWriter('UTF-8') {
            it.write(processTemplate(template.text))
        }

        // Create VCS .gitignore
        if (gitignore != null) {
            new File(dest, '.gitignore').withWriter('UTF-8') {
                it.write(processTemplate(gitignore.text))
            }
        }
    }

    def processTemplate(data) {
        data = data.replace('{distro}', distro)
        data = data.replace('{projectname}', "${project.name}-${distro}")

        for (def k : replace.keySet()) {
            def v = replace.get(k)
            data = data.replace(k, v ?: 'null')
        }

        def libs = []
        if (bundle != null) {
            def zf = new ZipFile(bundle)
            zf.entries().findAll{ it.name.equals('META-INF/libraries.list') }.each {
                zf.getInputStream(it).text.split('\r?\n').each { line ->
                    libs.add("'" + line.split('\t')[1] + "'")
                }
            }
        }
        if (meta != null) {
            def json = new JsonSlurper().parse(meta)
            libs += json.libraries.findAll { Utils.testJsonRules(it.rules) }.collect { lib -> "'${lib.name}' " }.unique { a, b -> a <=> b }
        }
        libs += libraries
        data = data.replace('{libraries}', 'implementation ' + libs.join('\n    implementation '))

        return data
    }
}
