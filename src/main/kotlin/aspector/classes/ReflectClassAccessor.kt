package aspector.classes

import aspector.generate.ClassMaker.Companion.asName
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

class ReflectClassAccessor(
  vararg loaders: ClassLoader
): ClassAccessor {
  companion object {
    private fun Annotation.asEAnnotation(): EAnnotation {
      val annoType = javaClass
      val pairs = annoType.methods.map { method ->
        method.isAccessible = true
        method.name to handleAnnotationValue(method.name, method.invoke(this))
      }

      return EAnnotation(
        annoType.asName(),
        pairs.toMap()
      )
    }

    private fun handleAnnotationValue(name: String, value: Any): AnnotationValue<*, *> {
      return when(value) {
        is ByteArray, is ShortArray, is IntArray, is LongArray,
        is FloatArray, is DoubleArray, is BooleanArray, is CharArray,
        is String, is java.lang.Byte, is java.lang.Short, is Integer, is java.lang.Long,
        is java.lang.Float, is java.lang.Double, is java.lang.Boolean, is Character ->
          Value(name, value)
        is Class<*> ->
          TypeValue(name, value.asName())
        is Enum<*> ->
          EnumValue(name, value.javaClass.asName(), value.name)
        is Array<*> ->
          Value(name, value.map { handleAnnotationValue(name, it!!) }.toTypedArray())
        else -> throw IllegalArgumentException("Unsupported value type: $value")
      }
    }
  }

  private val attachedClassLoader = loaders.takeIf { it.any() }?.toMutableList()
                                    ?: mutableListOf(ReflectClassAccessor::class.java.classLoader)

  private val loadedDeclMap = mutableMapOf<ClassName, ClassDecl<*>>()

  fun attachClassLoader(classLoader: ClassLoader) {
    attachedClassLoader += classLoader
  }

  private fun loadClass(className: ClassName): Class<*> {
    return when (className.descriptor) {
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
    return when (className.descriptor) {
      "V" -> ClassAccessor.voidDecl
      "B" -> ClassAccessor.byteDecl
      "S" -> ClassAccessor.shortDecl
      "I" -> ClassAccessor.intDecl
      "J" -> ClassAccessor.longDecl
      "F" -> ClassAccessor.floatDecl
      "D" -> ClassAccessor.doubleDecl
      "C" -> ClassAccessor.charDecl
      "Z" -> ClassAccessor.booleanDecl
      else -> {
        return loadedDeclMap.getOrPut(className) {
          if (className.isArray) {
            return ArrayClassDecl(loadClass(className.componentName))
          }

          val clazz = loadClass(className)
          ReflectClassDecl(this, clazz)
        } as ClassDecl<T>
      }
    } as ClassDecl<T>
  }

  private class ArrayClassDecl<T: Any>(
    clazz: Class<*>,
  ): ClassDecl<T>(ClassName.byClass(clazz)){
    override val flags: Int get() = Modifier.PUBLIC or Modifier.FINAL
    override val superClass: ClassDecl<*>? = null
    override val annotatedSuperClass: AnnotatedType<*>? = null
    override val interfaces: List<ClassDecl<*>> = emptyList()
    override val annotatedInterfaces: List<AnnotatedType<*>> = emptyList()
    override val fields: List<EField> = listOf(
      EField(
        this,
        "length",
        AnnotatedType(ClassAccessor.intDecl, emptyList()),
        Modifier.PUBLIC or Modifier.FINAL,
        null,
        emptyList(),
      )
    )
    override val constructors: List<EConstructor<T>> = emptyList()
    override val methods: List<EMethod> = emptyList()
    override val annotations: List<EAnnotation> = emptyList()
  }

  private class ReflectClassDecl<T : Any>(
    private val accessor: ClassAccessor,
    private val clazz: Class<T>
  ): ClassDecl<T>(ClassName.byClass(clazz)){
    override val flags: Int get() = clazz.modifiers
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
          field.annotations.map { it.asEAnnotation() },
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
            p.annotations.map { it.asEAnnotation() },
          ) },
          constructor.modifiers,
          constructor.annotations.map { it.asEAnnotation() },
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
            p.annotations.map { it.asEAnnotation() },
          ) },
          method.annotatedReturnType.toAnnoType<Any>(),
          method.modifiers,
          method.annotations.map { it.asEAnnotation() },
        )
      }
    }

    override val annotations: List<EAnnotation> by lazy {
      clazz.declaredAnnotations.map { it.asEAnnotation() }
    }

    private fun <T: Any> Class<T>.toDecl() = accessor.getClassDecl<T>(ClassName.byClass(this))

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> java.lang.reflect.AnnotatedType.toAnnoType(): AnnotatedType<T> =
      AnnotatedType((when(val t = type) {
        is ParameterizedType -> t.rawType as Class<T>
        is Class<*> -> t as Class<T>
        else -> throw UnsupportedOperationException("Unsupported type ${t.javaClass.name}")
      }).toDecl(), annotations.map { it.asEAnnotation() })

    private fun getSuperClassWithAnnotations(clazz: Class<*>): AnnotatedType<*>? {
      if (clazz.superclass == null) return null

      val list = mutableListOf<Annotation>()

      val superClass = clazz.kotlin.supertypes.find { !(it.classifier as KClass<*>).java.isInterface }
      superClass?.annotations?.forEach { list.add(it) }

      clazz.annotatedSuperclass?.annotations?.forEach { list.add(it) }

      return AnnotatedType(
        clazz.superclass.toDecl(),
        list.map { it.asEAnnotation() }
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
        val annotations = it.value.map { a -> a.asEAnnotation() }
        AnnotatedType(
          clazz.toDecl(),
          annotations
        )
      }
    }
  }
}