@file:Suppress("removal")

import core.IO
import core.commands.parser.Executable
import core.plugins.EsePlugin
import core.user.User
import core.utils.log
import core.vfs.Directory
import core.vfs.FileSystem
import core.vfs.Path.Companion.toPath
import core.vfs.dsl.dir
import core.vfs.dsl.executable
import core.vfs.dsl.fileDSL
import core.vfs.toDirectoryOrNull
import org.koin.core.component.inject
import java.io.File
import java.net.URL
import java.security.AccessController
import java.security.CodeSigner
import java.security.CodeSource
import java.security.Policy

class Main : EsePlugin {
    val fs by inject<FileSystem>()
    val io by inject<IO>()
    override fun init(user: User) {
        println("[Plugin] 到達")
        /*@Suppress("removal")
        Policy.getPolicy()!!.getPermissions(CodeSource(URL(null), emptyArray<CodeSigner>())).elements().toList().map {
            it.name
        }.log("[Plugin]")*/
        io.outputStream.println("インストール完了\nはろー！${user.export().name}さん！".log("[Plugin]"))
        File("/home/naotiki/").resolve("eselinux").mkdir()
    }

    /*override*/ fun init2(user: User) {
        val installDir = fs.tryResolve("/opt".toPath())?.toDirectoryOrNull()
            ?: throw Exception("ばーん💥💥💥")
        fileDSL(installDir, user) {
            dir("ExamplePlugin") {
                fs.fileTree.executableEnvPaths += dir("bin") {
                    executable(PluginCommand())
                }
            }
        }
        println("[Plugin] 到達")
        @Suppress("removal")
        Policy.getPolicy()!!.getPermissions(CodeSource(URL("a"), emptyArray<CodeSigner>())).elements().toList().map {
            it.name
        }.log("[Plugin]")
        io.outputStream.println("インストール完了\nはろー！${user.export().name}さん！".log("[Plugin]"))
    }
}

class PluginCommand : Executable<Unit>("example", "ExamplePluginプラグインによって追加されたコマンド") {
    override suspend fun execute(user: User, rawArgs: List<String>) {
        out.println("ExamplePlugin no komando dayo-")
    }
}
