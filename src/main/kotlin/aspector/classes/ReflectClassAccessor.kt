package aspector.classes

import aspector.classes.ClassAccessor.Companion.booleanDecl
import aspector.classes.ClassAccessor.Companion.byteDecl
import aspector.classes.ClassAccessor.Companion.charDecl
import aspector.classes.ClassAccessor.Companion.doubleDecl
import aspector.classes.ClassAccessor.Companion.floatDecl
import aspector.classes.ClassAccessor.Companion.intDecl
import aspector.classes.ClassAccessor.Companion.longDecl
import aspector.classes.ClassAccessor.Companion.shortDecl
import aspector.classes.ClassAccessor.Companion.voidDecl
import aspector.classes.elements.EConstructor
import aspector.classes.elements.EField
import aspector.classes.elements.EMethod
import aspector.classes.elements.Parameter
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

class ReflectClassAccessor(
  vararg loaders: ClassLoader
): ClassAccessor {
  private val attachedClassLoader = loaders.takeIf { it.any() }?.toMutableList()
                                    ?: mutableListOf(ReflectClassAccessor::class.java.classLoader)

  fun attachClassLoader(classLoader: ClassLoader) {
    attachedClassLoader += classLoader
  }

  private fun loadClass(className: ClassName): Class<*> {
    return when (className.signatureName) {
      "V" -> Void.TYPE!!
      "B" -> Byte::class.java
      "S" -> Short::class.java
      "I" -> Int::class.java
      "J" -> Long::class.java
      "F" -> Float::class.java
      "D" -> Double::class.java
      "C" -> Char::class.java
      "Z" -> Boolean::class.java
      else -> {
        if (className.isArray) {
          val componentType = loadClass(className.componentName)
          return componentType.arrayType()
        }

        val name = className.name

        return attachedClassLoader.firstNotNullOfOrNull {
          try { it.loadClass(name) }
          catch (_: ClassNotFoundException) { null }
        }?: throw ClassNotFoundException()
      }
    }
  }

  override fun getBytes(className: ClassName): ByteArray {
    val clazz = loadClass(className)
    val loader = clazz.classLoader

    val path = clazz.name.replace('.', '/') + ".class"

    return loader.getResourceAsStream(path)?.readAllBytes()
           ?: throw IllegalArgumentException("Class $clazz have no bytecode found.")
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> getClassDecl(className: ClassName): ClassDecl<T> {
    return when (className.signatureName) {
      "V" -> voidDecl
      "B" -> byteDecl
      "S" -> shortDecl
      "I" -> intDecl
      "J" -> longDecl
      "F" -> floatDecl
      "D" -> doubleDecl
      "C" -> charDecl
      "Z" -> booleanDecl
      else -> {
        if (className.isArray) {
          return ArrayClassDecl(loadClass(className.componentName))
        }

        val clazz = loadClass(className)
        ReflectClassDecl(this, clazz) as ClassDecl<T>
      }
    } as ClassDecl<T>
  }

  private class ArrayClassDecl<T: Any>(
    clazz: Class<*>,
  ): ClassDecl<T>(ClassName.by(clazz), clazz.modifiers){
    override val superClass: ClassDecl<*>? = null
    override val annotatedSuperClass: AnnotatedType<*>? = null
    override val interfaces: List<ClassDecl<*>> = emptyList()
    override val annotatedInterfaces: List<AnnotatedType<*>> = emptyList()
    override val fields: List<EField> = listOf(
      EField(
        this,
        "length",
        AnnotatedType(intDecl, emptyList()),
        Modifier.PUBLIC or Modifier.FINAL,
        null,
        emptyList(),
      )
    )
    override val constructors: List<EConstructor<T>> = emptyList()
    override val methods: List<EMethod> = emptyList()
  }

  private class ReflectClassDecl<T : Any>(
    private val accessor: ClassAccessor,
    private val clazz: Class<T>
  ): ClassDecl<T>(ClassName.by(clazz), clazz.modifiers){
    override val superClass: ClassDecl<*>? by lazy { clazz.superclass?.toDecl() }
    override val annotatedSuperClass: AnnotatedType<*>? by lazy { getSuperClassWithAnnotations(clazz) }

    override val interfaces: List<ClassDecl<*>> by lazy {
      clazz.interfaces.map { it.toDecl() }
    }
    override val annotatedInterfaces: List<AnnotatedType<*>> by lazy {
      val supertypes = getSuperTypesWithAnnotations(clazz)
      supertypes.filter { it.type.isInterface }
    }

    override val fields: List<EField> by lazy {
      clazz.declaredFields.map { field ->
        EField(
          field.declaringClass.toDecl(),
          field.name,
          field.annotatedType.toAnnoType<Any>(),
          field.modifiers,
          null,
          field.annotations.toList(),
        )
      }
    }
    @Suppress("UNCHECKED_CAST")
    override val constructors: List<EConstructor<T>> by lazy {
      clazz.declaredConstructors.map { constructor ->
        EConstructor(
          constructor.declaringClass.toDecl() as ClassDecl<T>,
          constructor.parameters.map { p -> Parameter(
            p.name,
            p.annotatedType.toAnnoType<Any>(),
            p.annotations.toList(),
          ) },
          constructor.modifiers,
          constructor.annotations.toList(),
        )
      }
    }
    @Suppress("UNCHECKED_CAST")
    override val methods: List<EMethod> by lazy {
      clazz.declaredMethods.map { method ->
        EMethod(
          method.declaringClass.toDecl() as ClassDecl<T>,
          method.name,
          method.parameters.map { p -> Parameter(
            p.name,
            p.annotatedType.toAnnoType<Any>(),
            p.annotations.toList(),
          ) },
          method.annotatedReturnType.toAnnoType<Any>(),
          method.modifiers,
          method.annotations.toList(),
        )
      }
    }

    private fun <T: Any> Class<T>.toDecl() = accessor.getClassDecl<T>(ClassName.by(this))

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> java.lang.reflect.AnnotatedType.toAnnoType(): AnnotatedType<T> =
      AnnotatedType((when(val t = type) {
        is ParameterizedType -> t.rawType as Class<T>
        is Class<*> -> t as Class<T>
        else -> throw UnsupportedOperationException("Unsupported type ${t.javaClass.name}")
      }).toDecl(), annotations.toList())

    private fun getSuperClassWithAnnotations(clazz: Class<*>): AnnotatedType<*>? {
      if (clazz.superclass == null) return null

      val list = mutableListOf<Annotation>()

      val superClass = clazz.kotlin.supertypes.find { !(it.classifier as KClass<*>).java.isInterface }
      superClass?.annotations?.forEach { list.add(it) }

      clazz.annotatedSuperclass?.annotations?.forEach { list.add(it) }

      return AnnotatedType(
        clazz.superclass.toDecl(),
        list
      )
    }

    private fun getSuperTypesWithAnnotations(clazz: Class<*>): List<AnnotatedType<*>> {
      val typeAnnotations = mutableMapOf<Class<*>, MutableList<Annotation>>()

      clazz.kotlin.supertypes.forEach {
        val annotations = it.annotations
        typeAnnotations.getOrPut((it.classifier as KClass<*>).java) { mutableListOf() }
          .addAll(annotations)
      }

      val javaTypes = (listOfNotNull(clazz.annotatedSuperclass) + clazz.annotatedInterfaces)
      javaTypes.forEach {
        typeAnnotations.getOrPut(it.type as Class<*>) { mutableListOf() }
          .addAll(it.annotations)
      }

      return typeAnnotations.map {
        val clazz = it.key
        val annotations = it.value
        AnnotatedType(
          clazz.toDecl(),
          annotations
        )
      }
    }
  }
}