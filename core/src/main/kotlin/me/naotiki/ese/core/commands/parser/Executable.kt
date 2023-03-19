package me.naotiki.ese.core.commands.parser


import me.naotiki.ese.core.EseError
import me.naotiki.ese.core.IO
import me.naotiki.ese.core.commands.dev.CommandDefineException
import me.naotiki.ese.core.user.User
import me.naotiki.ese.core.user.UserManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

abstract class CommandDefine<R>(val name: String, val description: String? = null) : KoinComponent {
    val args = mutableListOf<Arg<*>>()
    val opts = mutableListOf<Opt<*>>()

    val subCommands: List<SubCommandDefine<*>> = this::class.nestedClasses.filter {
        it.isInner && it.isSubclassOf(SubCommandDefine::class)
    }.map {
        it.primaryConstructor?.call(this)
    }.filterIsInstance<SubCommandDefine<*>>()

    abstract inner class SubCommandDefine<R>(name: String, description: String?) {
        val args = mutableListOf<Arg<*>>()
        val opts = mutableListOf<Opt<*>>()
    }
}

/**
 * すべてのコマンドの基底クラス
 * Ese Linux 内のコマンドはこのクラスを継承し、引数は[argument]や[option]で定義する必要があります。
 * [help]オプションはデフォルトで自動生成されています。
 * helpの挙動を変更するには[outputHelp]をオーバーライドしてください。
 *
 *
 * @param name コマンドの名前、[Expression.tryResolve]などで使用されます
 * @param description コマンドの説明、ヘルプで使用されます。(オプション)
 * @param R [execute]戻り値の型、基本は[Unit]
 * */
abstract class Executable<R>(val name: String, val description: String? = null) : KoinComponent {
    val um by inject<UserManager>()
    val io by inject<IO>()
    internal val argParser: SuperArgsParser = SuperArgsParser()
    val help by option(ArgType.Boolean, "help", "h", "ヘルプを表示します。").default(false)

    //サブコマンド
    val subCommands: List<SubCommand<*>> = this::class.nestedClasses.filter {
        it.isInner && it.isSubclassOf(SubCommand::class)
    }.map {
        it.primaryConstructor?.call(this)
    }.filterIsInstance<SubCommand<*>>()

    /**
     * @param type 引数の型
     * @param name 指定するときの名前 呼び出すときにプレフィックス"--"を付加する必要があります
     * @param shortName 指定するときの短い名前 一文字である必要があり、呼び出すときにプレフィックス"-"を付加する必要があります(オプション)
     * @param description helpで使用される説明(オプション)
     * */
    fun <T : Any> option(
        type: ArgType<T>, name: String, shortName: String? = null, description: String? = null
    ): Opt<T> {
        val o = Opt(type, name, shortName, description)
        argParser.opts.add(o)
        return o
    }


    /**
     * @param type 引数の型
     * @param name helpで表示される名前
     * @param description helpで使用される説明(オプション)
     * @param includeOption 引数にオプションを含めるかどうか
     * */
    fun <T : Any> argument(
        type: ArgType<T>, name: String, description: String? = null
    ): Arg<T> {
        if (subCommands.isNotEmpty())
            throw CommandDefineException("Executable having SubCommand can't have Args.")
        val a = Arg(type, name, description)
        argParser.args.add(a)
        return a
    }

    val out get() = io.printChannel
    val reader get() = io.reader
    val console by inject<me.naotiki.ese.core.ClientImpl>()

    /**
     * For Development
     * 解析情報出力用
     * */
    internal fun verbose(args: List<String>) {
        kotlin.runCatching {
            println(args)
            argParser.parse(this, args)

            out.tryPrintln("引数")
            argParser.args.forEach {
                out.tryPrintln("${it.name}/${it.type.javaClass.simpleName}:${it.vararg?.value ?: it.value}")
            }
            out.tryPrintln("オプション")
            argParser.opts.forEach {
                out.tryPrintln("${it.name}/${it.type.javaClass.simpleName}:${it.multiple?.value ?: it.value}")
            }
        }.onFailure {
            println(it.localizedMessage)
            out.tryPrintln(it.localizedMessage)
        }
    }

    fun generateHelpText() =
        buildString {
            appendLine("$name コマンドヘルプ")
            appendLine("構文")
            appendLine(
                "$name ${argParser.opts.joinToString(" ") { "[-${it.shortName}|--${it.name}]" }} " +
                        argParser.args.joinToString(" ") { it.name + if (it.vararg != null) "..." else "" }
            )
            appendLine("説明：$description")
            appendLine("引数")
            argParser.args.forEach {
                appendLine("${it.name}/${it.type.javaClass.simpleName}")
                appendLine(it.description)
            }
            appendLine("オプション")
            argParser.opts.forEach {
                appendLine("${it.name}/${it.type.javaClass.simpleName}")
                appendLine(it.description)
            }
        }

    /**
     * ヘルプを出力します。
     * */
    open fun outputHelp(): CommandResult.Nothing<R> {
        out.tryPrintln(
            generateHelpText()
        )
        return CommandResult.Nothing()
    }

    /**
     * 引数を解析して[execute]を実行します
     * @param args 解析前の引数
     * @return 結果
     * @throws CommandIllegalArgsException 型変換に失敗したとき
     * @throws CommandParserException 引数の形式が定義と異なるとき
     * */
    @Throws(CommandIllegalArgsException::class, CommandParserException::class)
    suspend fun resolve(user: User, args: List<String>): CommandResult<out Any?> = kotlin.runCatching {
        //if (isHelp(args)) return outputHelp()
        try {
            val subcommand = argParser.parse(this, args)
            if (subcommand != null)
                return subcommand.first.resolve(user, subcommand.second, args)
        } catch (_: CancellationException) {
            //Ctrl+Cを検知してしまうので握りつぶす
        } catch (e: Exception) {
            if (help) return outputHelp()
            throw e
        }
        if (help) {
            return outputHelp()
        }
        return@runCatching execute(user, args)
    }.fold(
        onSuccess = {
            CommandResult.Success(it)
        },
        onFailure = {
            if (it is EseError) {
                println(it.errorName)
                out.println(it.errorName)
            } else {
                it.printStackTrace()
                io.printChannel.tryPrintln(it.stackTraceToString())


            }
            CommandResult.Error()
        }
    )

    /**
     * 実際に実行される関数
     * @param rawArgs 生の引数
     * */
    protected abstract suspend fun execute(user: User, rawArgs: List<String>): R

    //TODO サブコマンド
    abstract inner class SubCommand<R>(val name: String, val description: String? = null) {
        private val argParser: SuperArgsParser = SuperArgsParser()
        fun <T : Any> option(
            type: ArgType<T>, name: String, shortName: String? = null, description: String? = null
        ): Opt<T> {
            val o = Opt(type, name, shortName, description)
            argParser.opts.add(o)
            return o
        }

        fun <T : Any> argument(
            type: ArgType<T>, name: String, description: String? = null
        ): Arg<T> {
            val a = Arg(type, name, description)
            argParser.args.add(a)
            return a
        }

        abstract suspend fun execute(user: User, rawArgs: List<String>): R

        /**
         * 引数を解析して[execute]を実行します
         * @param args 解析前の引数
         * @return 結果
         * @throws CommandIllegalArgsException 型変換に失敗したとき
         * @throws CommandParserException 引数の形式が定義と異なるとき
         * */
        @Throws(CommandIllegalArgsException::class, CommandParserException::class)
        suspend fun resolve(user: User, args: List<String>, rawArgs: List<String>): CommandResult<out Any?> =
            kotlin.runCatching {
                try {
                    argParser.parse(this@Executable, args, this)
                } catch (_: CancellationException) {

                } catch (e: Exception) {
                    if (help) {
                        //return outputHelp()
                    } else {
                        throw e
                    }
                }
                if (help) {
                    // return outputHelp()
                }

                execute(user, rawArgs)
            }.fold(
                onSuccess = {
                    CommandResult.Success(it)
                },
                onFailure = {
                    if (it is EseError) {
                        println(it.errorName)
                        out.println(it.errorName)
                    } else {
                        it.printStackTrace()
                        io.printChannel.tryPrintln(it.stackTraceToString())

                    }
                    CommandResult.Error()
                }
            )
    }
}


sealed interface CommandResult<T> {
    class Nothing<T> : CommandResult<T>
    class Success<T>(val value: T) : CommandResult<T>
    class Error<T> : CommandResult<T>

}
