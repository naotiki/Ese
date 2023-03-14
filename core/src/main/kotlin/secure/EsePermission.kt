package secure

import core.utils.log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Type
import secure.Permissions.*
import java.io.File
import java.nio.file.FileVisitor
import java.nio.file.Files

@Serializable
sealed interface InspectValue {
    @Serializable
    class AccessFlag(val flag: Int) : InspectValue

    @Serializable
    class SuperClass(val internalName: String) : InspectValue

    @Serializable
    class Owner(val internalName: String) : InspectValue

    @Serializable
    class OwnerPackage(val pacName: String) : InspectValue

    @Serializable
    class FuncCall(val owner: Owner, val funcName: String, val descriptor: String?) : InspectValue

    @Serializable
    class Field(val owner: Owner, val name: String, val putOnly: Boolean, val descriptor: String?) : InspectValue
}

@Serializable
enum class Permissions {
    FileAccess,
    Reflection,
    ClassLoad,
    ExternalExecute,
    NativeCall,
}
internal val defaultPermissions=permissionDSL {
    permission(FileAccess) {
        inspectOwnerPackage("java.nio.file")
        inspectOwner(File::class.java)
        inspectOwner(Files::class.java)
        inspectOwner(FileVisitor::class.java)
        inspectOwner(java.nio.file.FileSystem::class.java)
    }
    permission(Reflection) {
        inspectOwnerPackage("kotlin.reflect")
        inspectOwnerPackage("java.lang.reflect")
    }
    permission(ClassLoad) {
        inspectSuperClass(ClassLoader::class.java)
        inspectOwner(ClassLoader::class.java)
    }
    permission(ExternalExecute) {
        inspectOwner(ProcessBuilder::class.java)
        inspectFuncCall(Runtime::class.java, "exec")
    }
    permission(NativeCall) {
        inspectAccFlag(ACC_NATIVE)
    }
}
private fun main() {
    val map = defaultPermissions
    json.encodeToString(inspectSerializer,map).log()
}


@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}
typealias PermissionMap=Map<Permissions,List<InspectValue>>
val inspectSerializer = MapSerializer(Permissions.serializer(), ListSerializer(InspectValue.serializer()))
fun permissionDSL(block: PermissionDSL.() -> Unit): PermissionMap {
    val p = PermissionDSL()
    p.block()
    return p.createMap()
}

class PermissionContext(val list: MutableList<InspectValue>)
class PermissionDSL {
    private val maps: Map<Permissions, MutableList<InspectValue>> = Permissions.values().associateWith {
        mutableListOf()
    }

    fun permission(permission: Permissions, block: PermissionContext.() -> Unit) {
        PermissionContext(maps.getValue(permission)).block()
    }

    fun createMap(): PermissionMap = maps
}

fun PermissionContext.inspectAccFlag(opcode: Int) {
    list += InspectValue.AccessFlag(opcode)
}

fun PermissionContext.inspectOwner(clazz: Class<*>) {
    list += InspectValue.Owner(Type.getInternalName(clazz))
}

fun PermissionContext.inspectOwnerPackage(packageName: String) {
    list += InspectValue.OwnerPackage(packageName.replace(".", "/"))
}

fun PermissionContext.inspectFuncCall(clazz: Class<*>, name: String, descriptor: String? = null) {
    list += InspectValue.FuncCall(InspectValue.Owner(Type.getInternalName(clazz)), name, descriptor)
}

fun PermissionContext.inspectSuperClass(clazz: Class<*>) {
    list += InspectValue.SuperClass(Type.getInternalName(clazz))
}

/**
 * @param writeOnly 格納のみを制限する場合はtrue
 */
fun PermissionContext.inspectField(
    clazz: Class<*>,
    name: String,
    writeOnly: Boolean = false,
    descriptor: String? = null
) {
    list += InspectValue.Field(InspectValue.Owner(Type.getInternalName(clazz)), name, writeOnly, descriptor)
}