package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.nio.file.Files

import net.minecraftforge.srgutils.*

public class RenameSources extends DefaultTask {
    @InputDirectory input
    @InputFile srg
    @InputFile yarnmapping
    @InputFile official
    @OutputDirectory dest
    @OutputFile mapp
    
    @TaskAction
    def exec() {
        Utils.init()
        def names = loadMappings()
        def root = input.toPath()
        
        Files.walk(root).withCloseable{ stream ->
            stream.each { entry ->
                if(!Files.isDirectory(entry)) {
                    def path = root.relativize(entry).toString()
                    def out = new File(dest, path)
                    if (!out.parentFile.exists())
                        out.parentFile.mkdirs();
                        
                    def data
                    if (path.endsWith('.java')) {
                        data = entry.toFile().text.replaceAll(/f_\d+_|m_\d+_|p_\d+_|func_\d+_[a-zA-Z_]+|field_\d+_[a-zA-Z_]+|par_\d+_[a-zA-Z_]+/) { 
                            name -> names.getOrDefault(name, name)
                        } 
                    } else
                        data = entry.toFile().text
                        
                    Files.newBufferedWriter(out.toPath()).withCloseable { writer ->
                        writer.write(data)
                    }
                }
            }
        }
    }
    
    def loadMappings() {
        def msrg = IMappingFile.load(srg).reverse().rename(new IRenamer() {
                String rename(IMappingFile.IParameter par) {
                    return par.original
                }
            })
        def yarn = IMappingFile.load(yarnmapping)
        def map = msrg.chain(yarn)
        map.write(mapp.toPath(), IMappingFile.Format.TSRG2, false)
        
        def ret = [:]
        map.classes.each{scls ->
            scls.fields.each{ sfld ->
                if (sfld.original.startsWith('f_') || sfld.original.startsWith('field_'))
                    ret.put(sfld.original, sfld.mapped)
            }
            scls.methods.each{ smtd ->
                if (smtd.original.startsWith('m_') || smtd.original.startsWith('func_'))
                    ret.put(smtd.original, smtd.mapped)
                smtd.parameters.each{ spar ->
                    if (spar.original.startsWith('p_') || spar.original.startsWith('par_'))
                        ret.put(spar.original, spar.mapped)
                }
            }
        }
        
        return ret
    }
    
    def loadMappingsOld() {
        def ret = [:]
        def msrg = IMappingFile.load(srg)
        def moff = IMappingFile.load(official).reverse()
        msrg.classes.each{scls -> 
            def ocls = moff.getClass(scls.original)
            if (ocls != null) {
                scls.fields.each{ sfld ->
                    if (sfld.mapped.startsWith('f_') || sfld.mapped.startsWith('field_'))
                        ret.put(sfld.mapped, ocls.remapField(sfld.original))
                }
                scls.methods.each{ smtd ->
                    if (smtd.mapped.startsWith('m_') || smtd.mapped.startsWith('func_'))
                        ret.put(smtd.mapped, ocls.remapMethod(smtd.original, smtd.descriptor))
                }
            }
        }
        return ret
    }
}