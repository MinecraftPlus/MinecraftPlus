package org.minecraftplus.gradle.tasks

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.internal.impldep.org.testng.internal.YamlParser
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.security.MessageDigest
import java.util.regex.Pattern

class Utils {
    public static init() {
        String.metaClass.rsplit = { chr -> [delegate.substring(0, delegate.lastIndexOf(chr)), delegate.substring(delegate.lastIndexOf(chr)+1)] }
        String.metaClass.toMavenPath = {
            if (delegate == null) return null
            def tmp = delegate
            def ext = 'jar'
            def idx = tmp.indexOf('@')
            if (idx != -1) {
                ext = tmp.substring(idx+1)
                tmp = tmp.substring(0,idx)
            }
            def pts = tmp.split(':')
            return pts[0].replace('.', '/') + '/' + pts[1] + '/' + pts[2] + '/' + pts[1] + '-' + pts[2] + (pts.length > 3 ? '-' + pts[3] : '') + '.' + ext
        }
        File.metaClass.getSha1 = { !delegate.exists() ? null : MessageDigest.getInstance('SHA-1').digest(delegate.bytes).encodeHex().toString() }

        File.metaClass.getJson = { return delegate.exists() ? new JsonSlurper().parse(delegate) : [:] }
        File.metaClass.setJson = { json -> delegate.text = new JsonBuilder(json).toPrettyString() }

        File.metaClass.getYaml = { return delegate.exists() ? new Yaml().load((delegate as File).newInputStream()) : [:] }
        def options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.AUTO);
        options.setPrettyFlow(true);
        File.metaClass.setYaml = { yaml -> delegate.text = new Yaml(options).dump(yaml) }

        Map.metaClass.merge << { Map rhs -> merge(delegate, rhs) }
        Map.metaClass.nested = { String key ->
            def entry = delegate
            key.split('\\.').each { entry = entry?.get(it) }
            return entry
        }
    }

    static def fillVariables(List<String> args, Map<String, Object> props) {
        def ret = []
        args.each{ arg ->
            if (!arg.startsWith('{') || !arg.endsWith('}') || !props.containsKey(arg.substring(1, arg.length() -1))) {
                ret.add(arg)
            } else {
                def val = props.get(arg.substring(1, arg.length() - 1))
                ret.add(val instanceof File ? val.absolutePath : val.toString())
            }
        }
        return ret
    }

    static def testJsonRules(rules) {
        if (rules == null) return true
        def allow = false
        for (def rule : rules) {
            def matched = true
            if (rule.os != null) {
                if (rule.os.name != null)
                    matched &= getOsName().equals(rule.os.name)
                if (rule.os.version != null)
                    matched &= Pattern.compile(rule.os.version).matcher(System.getProperty('os.version')).find()
                if (rule.os.arch != null)
                    matched &= Pattern.compile(rule.os.arch).matcher(System.getProperty('os.arch')).find()
            }
            if (matched) allow = 'allow'.equals(rule.action)
        }
        return allow
    }

    static def getOsName() {
        def name = System.getProperty('os.name').toLowerCase(java.util.Locale.ENGLISH)
        if (name.contains('windows') || name.contains('win')) return 'windows'
        if (name.contains('linux') || name.contains('unix')) return 'linux'
        if (name.contains('osx') || name.contains('mac')) return 'osx'
        return 'unknown'
    }

    static def merge(Map lhs, Map rhs) {
        return rhs.inject(lhs.clone()) { map, entry ->
            if (map[entry.key] instanceof Map && entry.value instanceof Map) {
                map[entry.key] = merge(map[entry.key], entry.value)
            } else if (map[entry.key] instanceof Collection && entry.value instanceof Collection) {
                map[entry.key] += entry.value
            } else {
                map[entry.key] = entry.value
            }
            return map
        }
    }

    static def toolConfig(def cfg, def name, def defaults) {
        def ent = cfg.tools.get(name)
        def version = ent?.version ?: defaults.version ?: null

        return [
                version: version,
                args: ent?.args ?: defaults.args ?: [],
                jvmargs: ent?.jvmargs ?: defaults.jvmargs ?: [],
                path: version?.toMavenPath(),
                repo: ent?.repo ?: defaults.repo ?: 'https://maven.minecraftplus.org/'
        ]
    }

    static def projectConfig(def cfg) {
        return cfg.project
    }

    static def versionInfo(def cfg) {
        return cfg.get("version_info")
    }
}
