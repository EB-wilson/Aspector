package aspector.generate

import aspector.AspectDecl
import aspector.classes.BytecodeLoader
import aspector.classes.ClassAccessor
import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.ClassElement
import aspector.classes.EAspectMethod
import aspector.classes.EConstructor
import aspector.classes.EField
import aspector.classes.EMethod
import kotlin.reflect.KClass

abstract class ClassMaker(
  val bytesAccessor: ClassAccessor
) {
  companion object {
    @JvmStatic fun KClass<*>.asName() = ClassName.byClass(java)
    @JvmStatic fun Class<*>.asName() = ClassName.byClass(this)
  }

  fun <T: Any> makeClass(
    aspectDecl: ClassDecl<*>,
    targetClass: ClassDecl<T>,
    scope: AspectBuilder.() -> Unit
  ): AspectResult<T> {
    val builder = AspectBuilder(
      generateClassName(aspectDecl, targetClass),
      targetClass.flags,
      targetClass.name,
      aspectDecl.name
    )
    builder.scope()

    return AspectResult(builder)
  }

  abstract fun generateClassName(
    aspectImpl: ClassDecl<*>,
    targetClass: ClassDecl<*>
  ): ClassName

  abstract fun generateBytecode(
    builder: AspectBuilder
  ): ByteArray

  abstract fun loadClass(
    loader: BytecodeLoader,
    className: ClassName,
    bytecode: ByteArray,
  ): Class<*>

  @Suppress("UNCHECKED_CAST")
  inner class AspectResult<T: Any>(
    builder: AspectBuilder
  ): AspectDecl<T>(builder) {
    private val _bytecode: ByteArray by lazy { generateBytecode(builder) }

    override fun getClassName(): ClassName = context.className
    override fun getBytecode(): ByteArray = _bytecode
    override fun load(loader: BytecodeLoader): Class<T> = loadClass(
      loader,
      getClassName(),
      _bytecode
    ) as Class<T>
  }

  class AspectBuilder (
    val className: ClassName,
    val accessFlags: Int,
    val superClass: ClassName,
    val aspectDecl: ClassName,
  ) {
    val stubTypes: MutableList<ClassName> = mutableListOf()
    val interfaces: MutableList<ClassName> = mutableListOf()

    val aspectElements: MutableList<EAspectMethod> = mutableListOf()
    val implElements: MutableList<ClassElement> = mutableListOf()
    val superElements: MutableList<ClassElement> = mutableListOf()

    fun registerStubSpec(stub: ClassDecl<*>) { stubTypes.add(stub.name) }
    fun registerInterfaces(inter: ClassDecl<*>) { interfaces.add(inter.name) }
    fun registerAspectMethod(method: EAspectMethod) { aspectElements.add(method) }

    fun registerImplField(field: EField) = implElements.add(field)
    fun registerImplMethod(method: EMethod) = implElements.add(method)
    fun registerImplConstructor(constructor: EConstructor<*>) = implElements.add(constructor)

    fun registerSuperField(field: EField) = superElements.add(field)
    fun registerSuperMethod(method: EMethod) = superElements.add(method)
    fun registerSuperConstructor(constructor: EConstructor<*>) = superElements.add(constructor)
  }
}