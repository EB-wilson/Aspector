package aspector.accesses

import sun.reflect.ReflectionFactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.lang.reflect.Method
import java.util.*
import kotlin.also
import kotlin.collections.set
import kotlin.collections.toMutableSet
import kotlin.jvm.java
import kotlin.jvm.javaPrimitiveType

/**反模块化工具，用于强制对需要的模块开放模块的软件包。在使用前请调用[setup]函数初始化本工具。
 *
 * 此类行为可能完全打破模块化的访问保护，本身是不安全的，若非是必要情况，请尽量避免使用该类。
 *
 * 同时本类型的副作用会破坏获得[java.lang.reflect.Field]的限制，如[jdk.internal.reflect.Reflection]等本不可反射字段的类型，
 * 在本工具初始化后将可反射所有字段。
 *
 * **此类仅在JDK9之后可用，不可在更早的版本中引用此类的方法。**
 *
 * @author EBwilson
 */
object Demodulator {
  private lateinit var reflectionFactory: ReflectionFactory
  private lateinit var trustedLookup: MethodHandles.Lookup
  private lateinit var fieldFilterHandle: VarHandle

  private lateinit var opensHandle: VarHandle
  private lateinit var exportHandle: VarHandle

  private var exportNative: Method? = null

  private var initialized = false

  fun setup() {
    if (initialized) return

    reflectionFactory = ReflectionFactory::class.java.getDeclaredConstructor()
      .also { it.isAccessible = true }
      .newInstance()
    trustedLookup = reflectionFactory.newConstructorForSerialization(
      MethodHandles.Lookup::class.java,
      MethodHandles.Lookup::class.java.getDeclaredConstructor(
        Class::class.java, Class::class.java, Int::class.javaPrimitiveType
      )
    ).newInstance(Any::class.java, null, -1) as MethodHandles.Lookup

    val reflectClazz = Class.forName("jdk.internal.reflect.Reflection")
    fieldFilterHandle = trustedLookup.findStaticVarHandle(reflectClazz, "fieldFilterMap", Map::class.java)

    clearFieldFilter()

    val moduleClazz = Module::class.java
    opensHandle = trustedLookup.findVarHandle(moduleClazz, "openPackages", Map::class.java)
    exportHandle = trustedLookup.findVarHandle(moduleClazz, "exportedPackages", Map::class.java)

    makeModuleOpen(
      moduleClazz.module,
      "java.lang",
      Demodulator::class.java.module
    )

    exportNative = moduleClazz.getDeclaredMethod(
      "addExports0",
      moduleClazz, String::class.java, moduleClazz
    )
    exportNative!!.setAccessible(true)
    exportNative!!.invoke(
      null,
      moduleClazz.module, "java.lang", Demodulator::class.java.module
    )

    initialized = true
  }

  fun makeModuleOpen(from: Module, clazz: Class<*>, to: Module) {
    if (clazz.isPrimitive) return
    else if (clazz.isArray) makeModuleOpen(from, clazz.componentType, to)
    else makeModuleOpen(from, clazz.getPackage(), to)
  }

  fun makeModuleOpen(from: Module, pac: Package, to: Module) {
    if (isModuleOpen(from, pac, to)) return

    if (!initialized)
      throw kotlin.IllegalStateException("Demodulator is not initialized yet, must invoke setup() method.")

    makeModuleOpen(from, pac.name, to)
  }

  fun isModuleOpen(from: Module, pac: Package, to: Module): Boolean {
    Objects.requireNonNull(from)
    Objects.requireNonNull(to)

    return from.isOpen(pac.name, to)
  }

  @Suppress("UNCHECKED_CAST")
  fun makeModuleOpen(from: Module, pac: String, to: Module) {
    exportNative?.invoke(null, from, pac, to)

    var opensMap = opensHandle.getVolatile(from) as? MutableMap<String, MutableSet<Module>>
    if (opensMap == null) {
      opensMap = mutableMapOf()
      opensHandle.setVolatile(from, opensMap)
    }

    var exportsMap = exportHandle.getVolatile(from) as? MutableMap<String, MutableSet<Module>>
    if (exportsMap == null) {
      exportsMap = mutableMapOf()
      exportHandle.setVolatile(from, exportsMap)
    }

    val opens = opensMap.computeIfAbsent(pac) { e -> mutableSetOf() }
    val exports = exportsMap.computeIfAbsent(pac) { e -> mutableSetOf() }

    try {
      opens.add(to)
    } catch (e: UnsupportedOperationException) {
      val lis = opens.toMutableSet()
      lis.add(to)
      opensMap[pac] = lis
    }

    try {
      exports.add(to)
    } catch (e: UnsupportedOperationException) {
      val lis = exports.toMutableSet()
      lis.add(to)
      exportsMap[pac] = lis
    }
  }

  private fun clearFieldFilter() {
    val map = fieldFilterHandle.getVolatile() as? MutableMap<*, *>
    map?.clear()
  }
}
