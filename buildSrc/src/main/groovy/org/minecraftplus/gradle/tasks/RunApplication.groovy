package org.minecraftplus.gradle.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec

class RunApplication extends JavaExec {

    static def enum AppType {
        CLIENT, SERVER
    }

    @Input AppType type
    @Internal File workDir

    @Internal manifest
    @Internal assets
    @Internal String index

    @Override
    final void exec() {
        net.minecraftforge.mcpconfig.tasks.Utils.init()

        logger.lifecycle(" - type: {}", type)

        setWorkingDir(workDir)
        logger.lifecycle(" - working dir: {}", workingDir)

        // Load arguments from version manifest if running client
        if (type == AppType.CLIENT) {
            logger.lifecycle(" - manifest file: {}", manifest)
            def game_arguments = new ArrayList()
            game_arguments.addAll(args)
            game_arguments.addAll(manifest.json.arguments.game)
            // TODO: Process manifest game argument rules
            setArgs(game_arguments)
            // TODO: Process manifest jvm arguments with rules
        }

        setArgs(fillVariables(args, [
            'auth_player_name': 'minecraftplus',
            'version_name': 'minecraftplus',
            'version_type': 'minecraftplus',
            'game_directory': workDir,
            'assets_root': assets,
            'assets_index_name': index,

            // Credentials
            'auth_uuid': '0',
            'auth_access_token': '0',
            'user_type': 'minecraftplus',
        ]))
        logger.lifecycle(" - app arguments: {}", args.toString())
        logger.lifecycle(" - jvm arguments: {}", allJvmArgs.toString())

        if (!workDir.exists())
            workDir.mkdirs()

        logger.lifecycle("Running...")

        super.exec()
    }

    static def fillVariables(List<String> args, Map<String, Object> props) {
        def ret = []
        args.each{ arg ->
            if (!arg.startsWith('${') || !arg.endsWith('}') || !props.containsKey(arg.substring(2, arg.length() - 1))) {
                ret.add(arg)
            } else {
                def val = props.get(arg.substring(2, arg.length() - 1))
                ret.add(val instanceof File ? val.absolutePath : val.toString())
            }
        }
        return ret
    }
}
