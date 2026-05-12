package aspector.generate

import aspector.AspectDecl
import aspector.Using.*
import aspector.classes.BytecodeLoader
import aspector.classes.ClassAccessor
import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.EAspectMethod
import aspector.classes.EConstructor
import aspector.classes.EField
import aspector.classes.EMethod
import aspector.classes.MethodSignature
import kotlin.jvm.Throws
import kotlin.reflect.KClass

abstract class AspectFactory(
  val classAccessor: ClassAccessor
) {
  companion object {
    @JvmStatic fun KClass<*>.asName() = ClassName.byClass(java)
    @JvmStatic fun Class<*>.asName() = ClassName.byClass(this)
  }

  fun <T: Any> makeClass(
    targetClass: ClassDecl<T>,
    vararg aspectClasses: ClassDecl<*>,
    scope: AspectBuilder.() -> Unit
  ): AspectResult<T> {
    val builder = AspectBuilder(
      generateClassName(targetClass, *aspectClasses),
      targetClass.flags,
      targetClass.name,
      aspectClasses.map { it.name }
    )
    builder.scope()

    return AspectResult(builder)
  }

  abstract fun generateClassName(
    targetClass: ClassDecl<*>,
    vararg aspectClasses: ClassDecl<*>
  ): ClassName

  abstract fun generateBytecode(
    builder: AspectBuilder
  ): ByteArray

  abstract fun loadClass(
    loader: BytecodeLoader,
    className: ClassName,
    bytecode: ByteArray,
  ): Class<*>

  @Throws(Throwable::class)
  abstract fun checkAspectable(sourceClass: ClassDecl<*>, aspectClasses: List<ClassDecl<*>>)

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

  sealed class MethodUsing(
    val signature: MethodSignature,
    val layer: Int,
  ) {
    abstract fun addElement(method: EAspectMethod)
    abstract fun getElements(): List<EAspectMethod>
  }

  class SingleUsing(
    signature: MethodSignature,
    layer: Int,
    val method: EAspectMethod
  ): MethodUsing(signature, layer) {
    private val methods = mutableListOf<EAspectMethod>()

    val using = method.using
    val conflict get() = methods.size > 1

    init {
      when(using) {
        OVERRIDE, REPLACE -> {}
        else -> throw IllegalAspectDeclaringException("Illegal using. method: ${method.signature}")
      }
    }

    override fun addElement(method: EAspectMethod) {
      methods.add(method)
    }

    override fun getElements(): List<EAspectMethod> = methods
  }

  class MixinUsing(
    signature: MethodSignature,
    layer: Int,
  ): MethodUsing(signature, layer) {
    private val methods = mutableListOf<EAspectMethod>()

    override fun addElement(method: EAspectMethod) {
      when(method.using) {
        BEFORE, BEFORE_RETURN, AFTER, AFTER_RETURN -> {}
        else -> throw IllegalAspectDeclaringException("Aspect element with signature ${method.signature} was declared with ${method.using.name} using, but the layer was mixin.")
      }

      methods.add(method)
    }

    override fun getElements(): List<EAspectMethod> = methods
  }

  class AspectBuilder (
    val className: ClassName,
    val accessFlags: Int,
    val superClass: ClassName,
    val aspectDecl: List<ClassName>,
  ) {
    val stubAttaches: MutableMap<ClassName, ClassName> = mutableMapOf()
    val interfaces: MutableList<ClassName> = mutableListOf()

    val declFields: MutableMap<String, MutableList<EField>> = mutableMapOf()
    val sharedFields: MutableMap<String, EField> = mutableMapOf()
    val declMethods: MutableMap<MethodSignature, MutableList<EMethod>> = mutableMapOf()
    val declConstructors: MutableMap<MethodSignature, MutableList<EConstructor<*>>> = mutableMapOf()

    val aspectMethods: MutableMap<MethodSignature, MutableList<MethodUsing>> = mutableMapOf()

    fun registerStubSpec(stub: ClassName, attached: ClassName) {
      stubAttaches[stub] = attached
    }
    fun registerInterfaces(inter: ClassName) { interfaces.add(inter) }

    fun registerDeclField(field: EField) {
      declFields.getOrPut(field.name) { mutableListOf() }
        .add(field)
    }
    fun registerSharedField(field: EField) {
      sharedFields.putIfAbsent(field.name, field)
    }

    fun registerDeclMethod(method: EMethod) {
      declMethods
        .getOrPut(method.signature) { mutableListOf() }
        .add(method)
    }
    fun registerDeclConstructor(constructor: EConstructor<*>) {
      declConstructors
        .getOrPut(constructor.signature) { mutableListOf() }
        .add(constructor)
    }

    fun registerAspectMethod(layer: Int, method: EAspectMethod) {
      aspectMethods
        .getOrPut(method.signature) { mutableListOf() }
        .also { usings ->
          var using = usings.find { it.layer == layer }
          if (using == null) {
            using = when (method.using) {
              OVERRIDE, REPLACE -> SingleUsing(method.signature, layer, method)
              BEFORE, BEFORE_RETURN, AFTER, AFTER_RETURN -> MixinUsing(method.signature, layer)
            }
            usings.add(using)
          }
          using.addElement(method)
        }
    }
  }
}