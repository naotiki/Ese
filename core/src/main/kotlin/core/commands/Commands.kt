package core.commands

import core.ConsoleInterface
import core.Variable.expandVariable
import core.Vfs
import core.commands.parser.*
import core.newPrompt
import core.user.VUM
import core.vfs.Directory
import core.vfs.ExecutableFile
import core.vfs.FireTree
import core.vfs.TextFile
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.PrintStream

//Man
class help : Command<Unit>(
    "help", """
        helpを表示します
""".trimIndent()
) {
    val cmd by argument(ArgType.String, "cmd").vararg()
    override suspend fun execute(args: List<String>) {

    }
}

class Parse : Command<Unit>(
    "devp", """
    Print verbose log of parser  
    開発用 / For development
""".trimIndent()
) {
    val cmd by argument(ArgType.Command, "cmd")
    val bypassArgs by argument(ArgType.String,"args").vararg(true)
    override suspend fun execute(args: List<String>) {
        cmd.verbose(bypassArgs)

    }
}

class ListFile : Command<Unit>(
    "ls", """
    今いる場所のファイルを一覧表示します
""".trimIndent()
) {

    val detail by option(ArgType.Boolean, "list", "l", "ディレクトリの内容を詳細表示します。").default(false)
    val all by option(ArgType.Boolean, "all", "a", "すべてのファイルを一覧表示します。").default(false)
    val directory by argument(ArgType.Dir, "target", "一覧表示するディレクトリ").optional()
    override suspend fun execute(args: List<String>) {
        (directory ?: Vfs.currentDirectory).children.filter { (_,f)->!f.hidden||all }.forEach { (name, dir) ->
            if (detail) {
                dir.run {
                    out.println("$permission ${owner.name} ${ownerGroup.name} ??? 1970 1 1 09:00 $name")
                }
            } else out.print("$name ")
        }
        out.println()
    }
}




class Remove : Command<Unit>(
    "rm", """
    今いる場所のファイルを一覧表示します
""".trimIndent()
) {


    override suspend fun execute(args: List<String>) {
        val b = Args(args).getArg(ArgType.File, Vfs.currentDirectory) ?: let {
            out.println("引数の形式が正しくありません。")
            null
        } ?: return
        if (b is Directory) {
            if (b.children.isEmpty()) {
                if (b.parent?.removeChild(b) == true) {
                    out.println("${b.name}が削除されました")
                }
            }
        } else b.parent?.removeChild(b)
    }
}


class ChangeDirectory : Command<Unit>("cd") {
    val directory by argument(ArgType.Dir, "target", "一覧表示するディレクトリ")
    override suspend fun execute(args: List<String>) {
        val dir = directory//args.firstOrNull()?.let { Vfs.tryResolve(Path(it)) } as? Directory
        if (dir != null) {
            Vfs.setCurrentPath(dir)
        } else out.println("無効なディレクトリ")
    }
}

class Yes : Command<Unit>("yes") {
    val value by argument(ArgType.String,"value","出力する文字列").optional()
    override suspend fun execute(args: List<String>) {
        val b = value?:"yes"

        while (true) {
            out.println(b)
            //Bits per sec yield()にすると ASSERT: 51.500000 != 51.750000 って出るから適度な休憩をあげましょう
            delay(10)
        }
    }
}


//😼
class Cat : Command<Unit>("cat") {
    override suspend fun execute(args: List<String>) {
        val txt = Args(args).getArg(ArgType.File)
        if (txt is TextFile) {
            out.println(txt.content)
        } else out.println("無効なファイル")
    }
}

class Echo : Command<Unit>("echo") {
    override suspend fun execute(args: List<String>) {
        args.joinToString(" ").let { out.println(expandVariable(it)) }
    }
}

class Clear : Command<Unit>("clear") {
    override suspend fun execute(args: List<String>) {
        console.clear()
    }
}

class SugoiUserDo : Command<Unit>("sudo","SUDO ~Sugoi User DO~ すごいユーザーの権限でコマンドを実行します") {
    val cmd by argument(ArgType.Command,"command","実行するコマンドです")
    val targetArgs by argument(ArgType.String,"args","commandに渡す引数です").vararg(true)
    override suspend fun execute(args: List<String>) {
        out.println(
            """あなたはテキストファイルからsudoコマンドの講習を受けたはずです。
これは通常、以下の3点に要約されます:

    #1) 他人のプライバシーを尊重すること。
    #2) タイプする前に考えること。
    #3) 大いなる力には大いなる責任が伴うこと。"""
        )
        val n = console.newPrompt("実行しますか？(続行するにはあなたのユーザー名を入力) >>")
        if (n == VUM.user?.name) {
            cmd.resolve(targetArgs)
        } else {
            out.println("残念、無効なユーザー名")
        }
    }
}

class Exit : Command<Unit>("exit") {
    override suspend fun execute(args: List<String>) {
        out.println("終了します")
        console.exit()
    }
}


internal object CommandManager {
    private val _commandList = mutableMapOf<String, Command<*>>()
    val commandList get() = _commandList.toMap()
    var out: PrintStream? = null
    var reader: BufferedReader? = null
    var consoleImpl: ConsoleInterface? = null
    fun initialize(out: PrintStream, reader: BufferedReader, consoleImpl: ConsoleInterface,) {
        CommandManager.out = out
        CommandManager.reader = reader
        CommandManager.consoleImpl = consoleImpl
    }
    @Deprecated("用無し")
    fun initialize(out: PrintStream, reader: BufferedReader, consoleImpl: ConsoleInterface, vararg cmd: Command<*>) {
        _commandList.clear()
        _commandList.putAll(cmd.associateBy { it.name })
        CommandManager.out = out
        CommandManager.reader = reader
        CommandManager.consoleImpl = consoleImpl
    }

    fun add(cmd: Command<*>) {
        _commandList[cmd.name] = cmd
    }

    fun tryResolve(cmd:String): Command<*>?{
        FireTree.executableEnvPaths.forEach {
            it.children.entries.firstOrNull {(name,_)-> cmd==name }?.let {(_,f) ->
                if (f is ExecutableFile<*>){
                    return f.command
                }
            }
        }
        return null
    }

    //fun tryResolve(cmd: String): Command<*>? = _commandList[cmd]
}