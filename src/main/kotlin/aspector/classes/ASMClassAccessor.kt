package aspector.classes

import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.jar.JarFile
import java.util.zip.ZipFile


class ASMClassAccessor(
  vararg paths: Any,
): ClassAccessor {
  private val loaderPath = paths.filterIsInstance<ClassLoader>()
  private val filePath = paths.filterIsInstance<File>()

  private val loadedDeclMap = mutableMapOf<ClassName, ClassDecl<*>>()

  override fun <T : Any> getClassDecl(className: ClassName): ClassDecl<T> {
    when (className.descriptor) {
      "V" -> ClassAccessor.Companion.voidDecl
      "B" -> ClassAccessor.Companion.byteDecl
      "S" -> ClassAccessor.Companion.shortDecl
      "I" -> ClassAccessor.Companion.intDecl
      "J" -> ClassAccessor.Companion.longDecl
      "F" -> ClassAccessor.Companion.floatDecl
      "D" -> ClassAccessor.Companion.doubleDecl
      "C" -> ClassAccessor.Companion.charDecl
      "Z" -> ClassAccessor.Companion.booleanDecl
      else -> {
        if (className.isArray) {
          val componentType = getClassDecl<Any>(className.componentName)
          return ArrayClassDecl(componentType)
        }

        val bytecode = getBytes(className)
      }
    }

    TODO()
  }

  override fun getBytes(className: ClassName): ByteArray {
    val path = className.internalName + ".class"

    val stream = loaderPath.firstNotNullOfOrNull { it.getResourceAsStream(path) }
        ?: filePath.firstNotNullOfOrNull {
          if (it.isDirectory) {
            val target = File(it, path)
            if (target.exists()) target.inputStream() else null
          }
          else if (it.extension == ".jar" || it.extension == ".zip") {
            val zip = if (it.extension == ".jar") JarFile(it.absolutePath) else ZipFile(it.absolutePath)
            val entry = zip.getEntry(path)
            zip.getInputStream(entry)
          }
          else null
        }?: throw ClassNotFoundException("$className not found in any paths")

    try {
      return stream.readBytes()
    } finally {
      stream.close()
    }
  }

  private class ArrayClassDecl<T: Any>(
    classDecl: ClassDecl<*>,
  ): ClassDecl<T>(classDecl.name){
    override val flags: Int get() = Modifier.PUBLIC or Modifier.FINAL
    override val superClass: ClassDecl<*>? = null
    override val annotatedSuperClass: AnnotatedType<*>? = null
    override val interfaces: List<ClassDecl<*>> = emptyList()
    override val annotatedInterfaces: List<AnnotatedType<*>> = emptyList()
    override val fields: List<EField> = listOf(
      EField(
        this,
        "length",
        AnnotatedType(ClassAccessor.Companion.intDecl, emptyList()),
        Modifier.PUBLIC or Modifier.FINAL,
        null,
        emptyList(),
      )
    )
    override val constructors: List<EConstructor<T>> = emptyList()
    override val methods: List<EMethod> = emptyList()
    override val annotations: List<EAnnotation> = emptyList()
  }

  private class BytecodeClassDecl<T: Any>(
    name: ClassName,
    private val accessor: ClassAccessor,
    private val bytecode: ByteArray,
  ): ClassDecl<T>(name) {
    private var initialized = false

    private var _flags = 0
    private var _superClass: ClassDecl<*>? = null
    private var _interfaces: List<ClassDecl<*>> = emptyList()
    private var _annotations: List<EAnnotation> = emptyList()
    private var _annoRefMap: Map<Int, () -> Annotation> = emptyMap()

    override val flags: Int get() = also { initialize() }._flags
    override val superClass: ClassDecl<*>? get() = also { initialize() }._superClass
    override val interfaces: List<ClassDecl<*>> get() = also { initialize() }._interfaces
    override val annotations: List<EAnnotation> get() = also { initialize() }._annotations

    override val annotatedSuperClass: AnnotatedType<*>? by lazy {
      initialize()
      TODO()
    }
    override val annotatedInterfaces: List<AnnotatedType<*>> by lazy {
      initialize()
      TODO()
    }
    override val fields: List<EField> by lazy {
      initialize()
      TODO()
    }
    override val constructors: List<EConstructor<T>> by lazy {
      initialize()
      TODO()
    }
    override val methods: List<EMethod> by lazy {
      initialize()
      TODO()
    }

    fun initialize(){
      if (initialized) return

      var superClass = this.superClass

      val typeRefAnnoMap = mutableMapOf<Int, () -> Annotation>()

      val annotations = mutableListOf<Annotation>()
      val fields = mutableListOf<EField>()
      val methods = mutableListOf<EMethod>()
      val constructors = mutableListOf<EConstructor<T>>()

      val classReader = ClassReader(bytecode)
      val classRoot = ClassNode(Opcodes.ASM9)
      classReader.accept(
        classRoot,
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
      )

      _flags = classRoot.access
      _superClass = classRoot.superName
        ?.let{ accessor.getClassDecl<T>(ClassName.byInternalName(it)) }
        ?: accessor.getClassDecl<Any>(ClassName.jObject)
      _interfaces = classRoot.interfaces
        ?.toList()
        ?.map { accessor.getClassDecl<T>(ClassName.byInternalName(it)) }
        ?: emptyList()

      (classRoot.visibleAnnotations + classRoot.invisibleAnnotations).forEach{ annotation ->
        val annotationName = ClassName.byDescriptor(annotation.desc)
        val annotationClass = Class.forName(annotationName.name)

        val values = annotation.values.map { raw -> handleValue(raw) }

      }

      (classRoot.visibleTypeAnnotations + classRoot.invisibleTypeAnnotations).forEach{ annotation ->

      }

      classReader.accept(object : ClassVisitor(Opcodes.ASM9) {

        override fun visitTypeAnnotation(
          typeRef: Int,
          typePath: TypePath?,
          descriptor: String,
          visible: Boolean,
        ): AnnotationVisitor? {
          val ref = TypeReference(typeRef)
          when {

          }
          return null
        }

        override fun visitField(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          value: Any?,
        ): FieldVisitor? {

          return null
        }

        override fun visitMethod(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor? {
          return null
        }
      }, ClassReader.SKIP_DEBUG)

      initialized = true
    }

    private fun handleValue(raw: Any) {
      when (raw) {
        is Type -> raw.descriptor
        else -> raw
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <A : Annotation> instanceAnnotation(annotationType: Class<A>, values: MutableMap<String, Any>): A {
    return Proxy.newProxyInstance(
      annotationType.getClassLoader(),
      arrayOf<Class<*>>(annotationType),
    ){ obj, method, args ->
      return@newProxyInstance values[method.name]?: method.invoke(obj, *args)
    } as A
  }
}