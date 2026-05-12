package aspector

import aspector.accesses.PackageAccessHandler
import aspector.generate.AspectFactory
import aspector.generate.AspectFactory.Companion.asName
import aspector.classes.BytecodeClassLoader
import aspector.classes.BytecodeLoader
import aspector.classes.ClassAccessor
import aspector.classes.ReflectClassAccessor
import kotlin.collections.map
import kotlin.reflect.KClass

object RuntimeAspector {
  inline fun <T> withMaker(
    noinline makerFactory: (ClassAccessor) -> AspectFactory,
    noinline accessorFactory: ((ClassAccessor) -> PackageAccessHandler)? = null,
    vararg loaderPaths: ClassLoader,
    scope: AspectDelegate.() -> T
  ): T = withMaker(makerFactory, accessorFactory, *loaderPaths).scope()

  fun withMaker(
    makerFactory: (ClassAccessor) -> AspectFactory,
    accessorFactory: ((ClassAccessor) -> PackageAccessHandler)? = null,
    vararg loaderPaths: ClassLoader,
  ): AspectDelegate {
    val accessor = ReflectClassAccessor(*loaderPaths)
    val aspector = Aspector(makerFactory(accessor))
    val packageAccessor = accessorFactory?.invoke(accessor)

    return AspectDelegate(accessor, aspector, packageAccessor)
  }

  data class AspectDelegate(
    private val accessor: ClassAccessor,
    private val aspector: Aspector,
    private val packageAccessor: PackageAccessHandler?
  ) {
    private var aspectLoader: BytecodeLoader = BytecodeClassLoader(javaClass.classLoader)

    fun use(loader: BytecodeLoader) = also {
      aspectLoader = loader
    }

    fun <T: Any> KClass<T>.open(): KClass<T> = java.open().kotlin
    fun <T: Any> openPackage(target: KClass<T>): KClass<T> = openPackage(target.java).kotlin

    fun <T: Any> Class<T>.open(): Class<T> = packageAccessor?.getPackageAccessClass(this) ?:
      throw UnsupportedOperationException("Open package access must provide a PackageAccessHandler")
    fun <T: Any> openPackage(target: Class<T>): Class<T> = packageAccessor?.getPackageAccessClass(target) ?:
      throw UnsupportedOperationException("Open package not found")

    infix fun <T: Any> KClass<T>.apply(
      aspectDecl: KClass<*>
    ): DeclDelegate<T> = java.apply(aspectDecl.java)
    infix fun <T: Any> Class<T>.apply(
      aspectDecl: Class<*>
    ): DeclDelegate<T> = aspector.applyAspect(
      accessor.getClassDecl<T>(asName()),
      accessor.getClassDecl<Any>(aspectDecl.asName())
    ).let { DeclDelegate(it) }

    infix fun <T: Any> KClass<T>.apply(
      aspectDecl: List<KClass<*>>
    ): DeclDelegate<T> = java.apply(aspectDecl.map { it.java })
    infix fun <T: Any> Class<T>.apply(
      aspectDecl: List<Class<*>>
    ): DeclDelegate<T> = aspector.applyAspect(
      accessor.getClassDecl<T>(asName()),
      *aspectDecl.map { accessor.getClassDecl<Any>(it.asName()) }
        .toTypedArray()
    ).let { DeclDelegate(it) }

    fun <T: Any> applyAspect(
      target: Class<T>,
      vararg aspectDecl: Class<*>
    ) = target.apply(aspectDecl.toList())
    fun <T: Any> applyAspect(
      target: KClass<T>,
      vararg aspectDecl: KClass<*>
    ) = target.apply(aspectDecl.toList())

    infix fun <T : Any> DeclDelegate<T>.with(loader: BytecodeLoader) = load(loader)

    inner class DeclDelegate<T : Any>(
      private val decl: AspectDecl<T>,
    ){
      private var _aspectClass: Class<T>? = null

      val aspectClass: Class<T> get() = load()._aspectClass!!
      val className get() = decl.getClassName()
      val bytecode get() = decl.getBytecode()

      fun load(loader: BytecodeLoader = aspectLoader) = also {
        _aspectClass ?: let { _aspectClass = decl.load(loader) }
      }

      fun instance(): T = aspectClass.getDeclaredConstructor().newInstance()
      fun instance(vararg args: Any): T {
        val argsTypes = args.map { it::class.javaPrimitiveType }.toTypedArray()
        return aspectClass
          .getDeclaredConstructor(*argsTypes)
          .newInstance(*args)
      }
      fun instanceTyped(vararg args: TypePair<*>): T {
        val argsList = args.map { it.value }
        val argsTypes = args.map { it.type }
        return aspectClass
          .getDeclaredConstructor(*argsTypes.toTypedArray<Class<*>>())
          .newInstance(*argsList.toTypedArray<Any>())
      }
    }
  }
}