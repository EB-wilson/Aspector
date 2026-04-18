package aspector.classes

import aspector.generate.ClassMaker.Companion.asName
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypeReference
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.TypeAnnotationNode
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.metadata.KmAnnotation
import kotlin.metadata.KmAnnotationArgument
import kotlin.metadata.KmClassifier
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.annotations
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature

class ASMClassAccessor(
  vararg paths: Any = arrayOf(ASMClassAccessor::class.java.classLoader),
): ClassAccessor {
  companion object {
    private operator fun <T> List<T>?.plus(b: List<T>?): List<T> =
      (this?: emptyList()).toMutableList().also { it.addAll(b?: emptyList()) }.toList()
  }

  private val loaderPath = paths.filterIsInstance<ClassLoader>()
  private val filePath = paths.filterIsInstance<File>()

  private val loadedDeclMap = mutableMapOf<ClassName, ClassDecl<*>>()

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
      else -> loadedDeclMap.getOrPut(className) {
        if (className.isArray) {
          val componentType = getClassDecl<Any>(className.componentName)
          return ArrayClassDecl(componentType)
        }

        val bytecode = getBytes(className)
        BytecodeClassDecl<T>(
          className,
          this,
          bytecode
        )
      }
    } as ClassDecl<T>
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

  private class BytecodeClassDecl<T: Any>(
    name: ClassName,
    private val accessor: ClassAccessor,
    private val bytecode: ByteArray,
  ): ClassDecl<T>(name) {
    private var initialized = false

    private var _flags = 0
    private var _superClass: ClassDecl<*>? = null
    private var _annotatedSuperClass: AnnotatedType<*>? = null
    private var _interfaces: List<ClassDecl<*>> = emptyList()
    private var _annotatedInterfaces: List<AnnotatedType<*>> = emptyList()
    private var _annotations: List<EAnnotation> = emptyList()
    private var _fields: List<EField> = emptyList()
    private var _methods: List<EMethod> = emptyList()
    private var _constructors: List<EConstructor<T>> = emptyList()

    override val flags: Int get() = also { initialize() }._flags
    override val superClass: ClassDecl<*> get() = also { initialize() }._superClass!!
    override val annotatedSuperClass: AnnotatedType<*> get() = also { initialize() }._annotatedSuperClass!!
    override val interfaces: List<ClassDecl<*>> get() = also { initialize() }._interfaces
    override val annotations: List<EAnnotation> get() = also { initialize() }._annotations
    override val annotatedInterfaces: List<AnnotatedType<*>> get() = also { initialize() }._annotatedInterfaces
    override val fields: List<EField> get() = also { initialize() }._fields
    override val constructors: List<EConstructor<T>> get() = also { initialize() }._constructors
    override val methods: List<EMethod> get() = also { initialize() }._methods

    fun initialize(){
      if (initialized) return

      val fields = mutableListOf<EField>()
      val methods = mutableListOf<EMethod>()
      val constructors = mutableListOf<EConstructor<T>>()

      val classReader = ClassReader(bytecode)
      val classRoot = ClassNode(Opcodes.ASM9)
      classReader.accept(
        classRoot,
        ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES
      )

      _annotations = handleAnnotations(
        classRoot.visibleAnnotations + classRoot.invisibleAnnotations
      )
      val ktMetadata = _annotations.find { it.type == Metadata::class.asName() }
      val metadata = ktMetadata?.let {
        Metadata(
          kind = ktMetadata.getValue<Value<Int>>("k")?.value?: 1,
          metadataVersion = ktMetadata.getValue<Value<IntArray>>("mv")?.value?: intArrayOf(),
          data1 = ktMetadata.getValue<ArrayValue<String, Value<String>>>("d1")?.value?.toTypedArray()?: arrayOf(),
          data2 = ktMetadata.getValue<ArrayValue<String, Value<String>>>("d2")?.value?.toTypedArray()?: arrayOf(),
          extraString = ktMetadata.getValue<Value<String>>("xs")?.value?: "",
          packageName = ktMetadata.getValue<Value<String>>("pn")?.value?: "",
          extraInt = ktMetadata.getValue<Value<Int>>("k")?.value?: 0
        )
      }
      val kmClass = metadata?.let {
        (KotlinClassMetadata.readLenient(metadata) as? KotlinClassMetadata.Class)?.kmClass
      }

      val typeRefAnnoMap = handleTypeAnnotations(
        classRoot.visibleTypeAnnotations + classRoot.invisibleTypeAnnotations
      )
      kmClass?.supertypes?.forEach { kmType ->
        val className = when(val cf = kmType.classifier){
          is KmClassifier.Class -> { ClassName.byName(cf.name) }
          else -> TODO()
        }

        val index = if (classRoot.superName == className.internalName) -1
                         else classRoot.interfaces.indexOfFirst{ it == className.internalName }

        kmType.annotations.forEach { kmAnnotation ->
          val annotation = handleKmAnnotation(kmAnnotation)
          typeRefAnnoMap.getOrPut(TypeReference.newSuperTypeReference(index).value){ mutableListOf() }
            .add(annotation)
        }
      }

      _flags = classRoot.access
      _superClass = classRoot.superName
        ?.let{ accessor.getClassDecl<T>(ClassName.byInternalName(it)) }
      ?: accessor.getClassDecl<Any>(ClassName.jObject)
      _annotatedSuperClass = AnnotatedType(
        _superClass!!,
        typeRefAnnoMap.getOrElse(
          TypeReference.newSuperTypeReference(-1).value
        ) { emptyList() }
      )
      _interfaces = classRoot.interfaces
        ?.toList()
        ?.map { accessor.getClassDecl<T>(ClassName.byInternalName(it)) }
      ?: emptyList()
      _annotatedInterfaces = _interfaces.mapIndexed { i, type ->
        AnnotatedType(
          type,
          typeRefAnnoMap.getOrElse(
            TypeReference.newSuperTypeReference(i).value) {
            emptyList()
          }
        )
      }

      val kmFieldAnnoRef = mutableMapOf<String, MutableMap<Int, MutableList<EAnnotation>>>()
      val kmMethodAnnoRef = mutableMapOf<MethodSignature, MutableMap<Int, MutableList<EAnnotation>>>()
      kmClass?.properties?.forEach { property ->
        property.fieldSignature?.also { sign ->
          val name = sign.name
          val type = property.returnType
          kmFieldAnnoRef.getOrPut(name){ mutableMapOf() }
            .getOrPut(TypeReference.newTypeReference(TypeReference.FIELD).value){ mutableListOf() }
            .addAll( type.annotations.map { handleKmAnnotation(it) })
        }
        property.getterSignature?.also { sign ->
          val signature = MethodSignature.parse(sign.name, sign.descriptor)
          kmMethodAnnoRef.getOrPut(signature){ mutableMapOf() }
            .getOrPut(TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value){ mutableListOf() }
            .addAll( property.returnType.annotations.map { handleKmAnnotation(it) })
        }
        property.setterSignature?.also { sign ->
          val signature = MethodSignature.parse(sign.name, sign.descriptor)
          kmMethodAnnoRef.getOrPut(signature){ mutableMapOf() }
            .getOrPut(TypeReference.newFormalParameterReference(0).value){ mutableListOf() }
            .addAll( property.returnType.annotations.map { handleKmAnnotation(it) })
        }
      }
      classRoot.fields.forEach { field ->
        val typeRefAnnoMap = handleTypeAnnotations(
          field.visibleTypeAnnotations + field.invisibleTypeAnnotations
        )

        kmFieldAnnoRef[field.name]?.forEach { (ref, list) ->
          typeRefAnnoMap.getOrPut(ref){ mutableListOf() }.addAll(list)
        }

        fields.add(EField(
          this,
          field.name,
          AnnotatedType(
            accessor.getClassDecl(ClassName.byDescriptor(field.desc)),
            typeRefAnnoMap.getOrElse(
              TypeReference.newTypeReference(TypeReference.FIELD).value
            ) { emptyList() }
          ),
          field.access,
          field.value,
          handleAnnotations(classRoot.visibleAnnotations + classRoot.invisibleAnnotations)
        ))
      }

      kmClass?.functions?.forEach { function ->
        val funcSign = function.signature?: return@forEach
        val sign = MethodSignature.parse(funcSign.name, funcSign.descriptor)
        val map = kmMethodAnnoRef.getOrPut(sign) { mutableMapOf() }

        function.returnType.annotations.forEach { kmAnnotation ->
          val annotation = handleKmAnnotation(kmAnnotation)
          map.getOrPut(TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value){ mutableListOf() }
            .add(annotation)
        }
        (listOfNotNull(function.receiverParameterType) + function.valueParameters.map { it.type })
          .forEachIndexed { i, paramType ->
            val annotations = paramType.annotations
            map.getOrPut(TypeReference.newFormalParameterReference(i).value) { mutableListOf() }
              .addAll(annotations.map { handleKmAnnotation(it) })
          }
      }
      classRoot.methods.forEach { method ->
        val signature = MethodSignature.parse(method.name, method.desc)
        val typeRefAnnoMap = handleTypeAnnotations(
          method.visibleTypeAnnotations + method.invisibleTypeAnnotations
        )
        val paramAnnotations = Array<MutableList<EAnnotation>>(signature.paramTypes.size) { mutableListOf() }
        method.visibleParameterAnnotations?.forEachIndexed { i, annotations ->
          paramAnnotations[i].addAll(handleAnnotations(annotations))
        }
        method.invisibleParameterAnnotations?.forEachIndexed { i, annotations ->
          paramAnnotations[i].addAll(handleAnnotations(annotations))
        }

        kmMethodAnnoRef[signature]?.forEach { (ref, list) ->
          typeRefAnnoMap.getOrPut(ref){ mutableListOf() }.addAll(list)
        }

        val paramNames = method.parameters?.map { it.name }
        val params = signature.paramTypes.mapIndexed { i, param ->
          Parameter(
            paramNames?.get(i)?: "arg$i",
            AnnotatedType(
              accessor.getClassDecl(signature.paramTypes[i]),
              typeRefAnnoMap.getOrElse(
                TypeReference.newFormalParameterReference(i).value
              ) { emptyList() }
            ),
            paramAnnotations[i].toList()
          )
        }

        if (method.name != "<init>") {
          methods.add(EMethod(
            this,
            method.name,
            params,
            AnnotatedType(
              accessor.getClassDecl(signature.returnType),
              typeRefAnnoMap.getOrElse(
                TypeReference.newTypeReference(TypeReference.METHOD_RETURN).value
              ) { emptyList() }
            ),
            method.access,
            handleAnnotations(method.visibleAnnotations + method.invisibleAnnotations)
          ))
        }
        else {
          constructors.add(EConstructor<T>(
            this,
            params,
            method.access,
            handleAnnotations(method.visibleAnnotations + method.invisibleAnnotations)
          ))
        }
      }

      _fields = fields
      _methods = methods
      _constructors = constructors

      initialized = true
    }

    private fun handleTypeAnnotations(nodes: List<TypeAnnotationNode>): MutableMap<Int, MutableList<EAnnotation>>{
      val typeRefAnnoMap = mutableMapOf<Int, MutableList<EAnnotation>>()
      nodes.forEach{ annotation ->
        val ref = annotation.typeRef
        typeRefAnnoMap.getOrPut(ref){ mutableListOf() }.add(handleAnnotation(annotation))
      }

      return typeRefAnnoMap
    }

    private fun handleAnnotations(nodes: List<AnnotationNode>): List<EAnnotation> {
      return nodes.map { handleAnnotation(it) }
    }

    private fun handleAnnotation(node: AnnotationNode): EAnnotation {
      val annotationName = ClassName.byDescriptor(node.desc)

      val annoValues = mutableMapOf<String, AnnotationValue<*, *>>()
      val valueList = node.values?: emptyList()
      for (i in valueList.indices step 2) {
        val name = valueList[i] as String
        val raw = valueList[i + 1]
        val value = handleAnnotationValue(raw)

        annoValues[name] = value
      }

      return EAnnotation(
        annotationName,
        annoValues.toMap()
      )
    }

    private fun handleKmAnnotation(kmAnnotation: KmAnnotation): EAnnotation {
      val annotationName = ClassName.byInternalName(kmAnnotation.className)

      val annoValues = mutableMapOf<String, AnnotationValue<*, *>>()
      kmAnnotation.arguments.forEach { arg ->
        val name = arg.key
        val raw = arg.value
        val value = handleKmAnnoArg(raw)

        annoValues[name] = value
      }

      return EAnnotation(
        annotationName,
        annoValues.toMap()
      )
    }

    private fun handleKmAnnoArg(argument: KmAnnotationArgument): AnnotationValue<*, *> {
      return when (argument) {
        is KmAnnotationArgument.LiteralValue<*> -> Value(argument.value)
        is KmAnnotationArgument.EnumValue -> EnumValue(
          ClassName.byName(argument.enumClassName),
          argument.enumEntryName
        )
        is KmAnnotationArgument.KClassValue -> TypeValue(ClassName.byName(argument.className))
        is KmAnnotationArgument.AnnotationValue -> NestedAnnotationValue(
          handleKmAnnotation(argument.annotation)
        )
        is KmAnnotationArgument.ArrayValue -> when(argument.elements.first()) {
          is KmAnnotationArgument.ByteValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.ByteValue).value }.toByteArray())
          is KmAnnotationArgument.ShortValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.ShortValue).value }.toShortArray())
          is KmAnnotationArgument.IntValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.IntValue).value }.toIntArray())
          is KmAnnotationArgument.LongValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.LongValue).value }.toLongArray())
          is KmAnnotationArgument.FloatValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.FloatValue).value }.toFloatArray())
          is KmAnnotationArgument.DoubleValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.DoubleValue).value }.toDoubleArray())
          is KmAnnotationArgument.BooleanValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.BooleanValue).value }.toBooleanArray())
          is KmAnnotationArgument.CharValue ->
            Value(argument.elements.map { (it as KmAnnotationArgument.CharValue).value }.toCharArray())
          else -> ArrayValue(argument.elements.map { handleKmAnnoArg(it) })
        }
        is KmAnnotationArgument.ArrayKClassValue -> TypeValue(let {
          var c = ClassName.byName(argument.className)
          for (n in 0 until argument.arrayDimensionCount) { c = c.arrayName }
          c
        })
      }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleAnnotationValue(raw: Any): AnnotationValue<*, *> {
      return when (raw) {
        is Type -> TypeValue(ClassName.byInternalName(raw.internalName))
        is Array<*> -> EnumValue(
          ClassName.byDescriptor(raw[0] as String),
          raw[1] as String
        )
        is List<*> -> when(raw.firstOrNull()) {
          is java.lang.Byte -> Value((raw as List<Byte>).toByteArray())
          is java.lang.Short -> Value((raw as List<Short>).toShortArray())
          is Integer -> Value((raw as List<Int>).toIntArray())
          is java.lang.Long -> Value((raw as List<Long>).toLongArray())
          is java.lang.Float -> Value((raw as List<Float>).toFloatArray())
          is java.lang.Double -> Value((raw as List<Double>).toDoubleArray())
          is java.lang.Boolean -> Value((raw as List<Boolean>).toBooleanArray())
          is Character -> Value((raw as List<Char>).toCharArray())
          null -> ArrayValue<Any, AnnotationValue<Any, Any>>(emptyList())
          else -> ArrayValue(raw.map { handleAnnotationValue(it!!) })
        }
        is AnnotationNode -> NestedAnnotationValue(handleAnnotation(raw))
        else -> Value(raw)
      }
    }
  }
}