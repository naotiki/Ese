package core.vfs

import core.EventManager
import core.commands.parser.Executable
import core.user.Group
import core.user.User


/**
 * 仮想的ファイルの抽象クラス
 * ディレクトリもファイルとする。
 *  @param name ファイルの名前
 *  @param parent 親ディレクトリ、ルートの場合はnull
 *  @param hidden 属性 [Boolean]をとる
 */

open class File(
    var name: String,
    var parent: Directory? = null,
    var hidden: Boolean,
    var owner: User,
    var ownerGroup: Group,
    var permission: Permission
) {
    fun getFullPath(): Path {
        val path = mutableListOf<String>()
        var f: File? = this
        while (f?.parent != null) {
            path.add(f.name)
            f = f.parent
        }
        return Path(path.reversed().joinToString("/", "/"))
    }

}

fun File.toDirectoryOrNull(): Directory? {
    return if (this is Directory) {
        this
    } else null
}


/**
 * 表示可能な文字列を持つファイル
 * @param [content] 内容
 * */
class TextFile(
    name: String,
    parent: Directory?,
    content: String,
    owner: User,
    group: Group,
    permission: Permission,
    hidden: Boolean
) : File(name, parent, owner = owner, ownerGroup = group, hidden = hidden, permission = permission) {
    var content = content
        private set
}

class ExecutableFile<R>(
    name: String, parent: Directory?, executable: Executable<R>, owner: User, group: Group, permission: Permission,
    hidden: Boolean
) : File(name, parent, owner = owner, ownerGroup = group, hidden = hidden, permission = permission) {
    var executable = executable
        private set
}

open class Directory(
    name: String, parent: Directory?, owner: User, group: Group, permission: Permission,
    hidden: Boolean
) : File(
    name, parent = parent, owner = owner, ownerGroup = group, hidden = hidden, permission = permission
) {
    open var _children: MutableMap<String, File> = mutableMapOf()
    fun getChildren(user: User, includeHidden: Boolean = false): Map<String, File>? {
        return if (
            permission.has(
                when {
                    user == owner -> {
                        PermissionTarget.OwnerR
                    }

                    user.group == ownerGroup -> {
                        PermissionTarget.GroupR
                    }

                    else -> {
                        PermissionTarget.OtherR
                    }
                }
            )
        ) _children.filterValues { !it.hidden || includeHidden }.toMap() else null


    }

    fun addChildren(vararg childDir: File) {
        _children.putAll(childDir.associateBy { it.name })
    }

    fun removeChild(childDir: File): Boolean {
        return _children.remove(childDir.name) != null
    }
}

// 進捗状況に応じて中身が変わるディレクトリ TODO 実装やれ
class DynamicDirectory(
    name: String,
    parent: Directory?,
    owner: User,
    group: Group,
    permission: Permission,
    hidden: Boolean
) :
    Directory(name, parent, owner, group, permission, hidden) {
    init {
        EventManager.addEventListener {

        }
    }

    override var _children: MutableMap<String, File>
        get() = super._children
        set(value) {}
}



