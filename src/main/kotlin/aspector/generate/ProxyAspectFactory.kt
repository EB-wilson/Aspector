package aspector.generate

import aspector.Using
import aspector.classes.BytecodeLoader
import aspector.classes.ClassName.Companion.B
import aspector.classes.ClassName.Companion.C
import aspector.classes.ClassName.Companion.D
import aspector.classes.ClassName.Companion.F
import aspector.classes.ClassName.Companion.I
import aspector.classes.ClassName.Companion.J
import aspector.classes.ClassName.Companion.S
import aspector.classes.ClassName.Companion.V
import aspector.classes.ClassName.Companion.Z
import aspector.classes.ClassAccessor
import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.MethodSignature
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier

class ProxyAspectFactory private constructor(
  classAccessor: ClassAccessor,
): AspectFactory(classAccessor){
  companion object {
    @JvmStatic
    fun create(
      accessor: ClassAccessor
    ): ProxyAspectFactory = ProxyAspectFactory(accessor)

    @JvmStatic
    fun factory(): (ClassAccessor) -> AspectFactory = { ProxyAspectFactory(it) }
  }

  override fun generateClassName(
    targetClass: ClassDecl<*>,
    vararg aspectClasses: ClassDecl<*>,
  ) = ClassName.byName(
    "${targetClass.name.name.let {
      if (it.startsWith("java.")) it.replace("java.", "javas.")
      else it
    } }$${aspectClasses.map { it.name }.hashCode().toHexString()}"
  )

  override fun generateBytecode(builder: AspectBuilder): ByteArray {
    val thisClass = builder.className
    val accessFlags = builder.accessFlags
    val stubAttaches = builder.stubAttaches
    val superClass = builder.superClass
    val aspectDecl = builder.aspectDecl
    val implements = builder.interfaces

    val superNode = classAccessor.getBytes(superClass).let { bytes ->
      val cr = ClassReader(bytes)
      val node = ClassNode(Opcodes.ASM9)
      cr.accept(node, ClassReader.SKIP_DEBUG)
      node
    }
    val superConstructors = superNode.methods.filter {
      it.name == "<init>" && it.access and Opcodes.ACC_PRIVATE == 0
    }

    val declBytes = aspectDecl.associateWith { classAccessor.getBytes(it) }
    val declNodes = declBytes.map { (cn, bytes) ->
      val cr = ClassReader(bytes)
      val implRoot = ClassNode(Opcodes.ASM9)
      cr.accept(implRoot, ClassReader.SKIP_DEBUG)
      cn to implRoot
    }.toMap()
    val declMethods = declNodes.map { (cn, node) ->
      cn to node.methods.associateBy { m -> MethodSignature.parse(m.name, m.desc) }
    }.toMap()

    val aspectElements = builder.aspectMethods

    val sharedFields = builder.sharedFields
    val fields = builder.declFields
    val methods = builder.declMethods
    val constructors = builder.declConstructors.let { map ->
      val allowedSignature = MethodSignature.parse("<init>", "()V")

      map.toList()
        .find { allowedSignature != it.first }
        ?.also {
          throw IllegalArgumentException("Constructors with parameters cannot exist in the context of Aspect declare. decl: ${it.second.first().declaring}")
        }
      map[allowedSignature]?:emptyList()
    }

    val methodMapping = methods.map {
      it.key to it.value.associate { method ->
        val methodSignature = MethodSignature.parse(
          "${method.name}$${method.declaring.name.hashCode().toHexString()}",
          method.signature.jvmDescriptor()
        )
        method.declaring.name to methodSignature
      }.toMap().toMutableMap()
    }.toMap().toMutableMap()

    aspectElements.forEach { (sign, list) ->
      val map = methodMapping.getOrPut(sign) { mutableMapOf() }

      list.flatMap { it.getElements() }.forEach { method ->
        val methodSignature = MethodSignature.parse(
          "${method.name}Aspect$${method.declaring.name.hashCode().toHexString()}",
          method.signature.jvmDescriptor()
        )
        map[method.declaring.name] = methodSignature
      }
    }

    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    cw.visit(
      Opcodes.V1_8,
      accessFlags or Opcodes.ACC_SUPER,
      thisClass.internalName,
      null,
      superClass.internalName,
      implements
        .map { it.internalName }
        .toTypedArray(),
    )

    // Generate shared fields
    sharedFields.values.forEach {
      val fieldVisitor = cw.visitField(
        it.flags,
        it.name,
        it.type.name.descriptor,
        null,
        it.constant
      )
      fieldVisitor.visitEnd()
    }

    // Generate aspect fields
    val fieldMapping = fields.map {
      it.key to it.value.associate { field ->
        val fieldName = "${field.name}$${field.declaring.name.hashCode().toHexString()}"
        val fieldVisitor = cw.visitField(
          field.flags,
          fieldName,
          field.type.name.descriptor,
          null,
          field.constant
        )
        fieldVisitor.visitEnd()
        field.declaring.name to fieldName
      }
    }.toMap()

    // Generate bridge methods
    // Constructor bridge
    val constructorList = constructors.map { constructor ->
      val declaring = constructor.declaring
      val realName = "init$${declaring.name.hashCode().toHexString()}"

      val byMethod = declMethods[declaring.name]?.get(constructor.signature)
                     ?: throw IllegalAspectDeclaringException("No such constructor declared in aspect class ${declaring.name}.")

      val methodVisitor = cw.visitMethod(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_BRIDGE,
        realName,
        constructor.signature.jvmDescriptor(),
        null,
        null
      )
      methodVisitor.visitCode()
      val list = byMethod.instructions
        .toList()
        .subList(2 + constructor.parameters.size, byMethod.instructions.size())
      val newInsnList = InsnList()
      list.forEach { newInsnList.add(it) }
      byMethod.instructions = newInsnList
      methodVisitor.visitMethodBy(
        byMethod,
        thisClass, superClass, aspectDecl,
        fieldMapping, methodMapping, stubAttaches
      )
      methodVisitor.visitMaxs(0, 0)
      methodVisitor.visitEnd()

      MethodSignature.parse(realName, "()V")
    }
    // Aspect method bridges
    aspectElements.forEach { (sign, elements) ->
      elements.flatMap { it.getElements() }.forEach { element ->
        val declaring = element.declaring
        val realSignature = methodMapping[sign]!![declaring.name]!!

        val byMethod = declMethods[declaring.name]?.get(sign)
                       ?: throw IllegalAspectDeclaringException("No such method declared in aspect class ${declaring.name}.")

        val methodVisitor = cw.visitMethod(
          Opcodes.ACC_PRIVATE,
          realSignature.methodName,
          sign.jvmDescriptor(),
          null,
          null
        )
        methodVisitor.visitCode()
        methodVisitor.visitMethodBy(
          byMethod,
          thisClass, superClass, aspectDecl,
          fieldMapping, methodMapping, stubAttaches
        )
        methodVisitor.visitMaxs(0, 0)
        methodVisitor.visitEnd()
      }
    }

    // Generate constructors
    superConstructors.forEach { constructor ->
      val methodVisitor = cw.visitMethod(
        constructor.access,
        constructor.name,
        constructor.desc,
        constructor.signature,
        constructor.exceptions.toTypedArray()
      )
      methodVisitor.visitCode()

      methodVisitor.invokeMethod(
        Opcodes.INVOKESPECIAL,
        superClass,
        MethodSignature.parse(constructor.name, constructor.desc),
        false
      )
      constructorList.forEach { constructor ->
        methodVisitor.invokeMethod(
          Opcodes.INVOKESPECIAL,
          thisClass,
          constructor,
          false
        )
      }
      methodVisitor.visitInsn(Opcodes.RETURN)
      methodVisitor.visitMaxs(0, 0)
      methodVisitor.visitEnd()
    }

    // Generate aspect methods
    aspectElements.forEach { (sign, elements) ->
      when(val methodUsing = elements.first()) {
        is MixinUsing -> {
          val methods = elements
            .flatMap { it.getElements() }
            .sortedBy { it.using.ordinal }

          val desc = sign.jvmDescriptor()
          val superMethod = superNode.methods.find {
            it.name == sign.methodName
            && it.desc == desc
          }

          val insert = methods.indexOfFirst { it.using == Using.AFTER || it.using == Using.AFTER_RETURN }
          val mixinList = methods.map { it.declaring.name }.let {
            if (superMethod != null) it.subList(0, insert) + superClass + it.subList(insert, elements.size)
            else it
          }

          val returnDeclaring = methods
            .find { it.using == Using.BEFORE_RETURN || it.using == Using.AFTER_RETURN }?.declaring?.name
            ?: superMethod?.let { superClass }
            ?: if (sign.returnType != V)
              throw IllegalAspectDeclaringException("Mixin method with signature $sign have no return value declared, and this method no existed target in super class $superClass.") else null

          val methodVisitor = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            sign.methodName,
            sign.jvmDescriptor(),
            null,
            null
          )
          methodVisitor.visitCode()

          mixinList.forEach { declaring ->
            val invokeBridge = methodMapping[sign]!![declaring]!!

            methodVisitor.invokeMethod(
              Opcodes.INVOKESPECIAL,
              thisClass,
              invokeBridge,
              false
            )
            if (sign.returnType != V && declaring != returnDeclaring) methodVisitor.visitInsn(Opcodes.POP)
          }
          methodVisitor.returnValue(sign.returnType)

          methodVisitor.visitMaxs(0, 0)
          methodVisitor.visitEnd()
        }
        is SingleUsing -> {
          if (methodUsing.conflict)
            throw IllegalAspectDeclaringException("Method ${methodUsing.method.name} in aspect declare have conflict with other aspect method with same layer and signature.")

          val method = methodUsing.method

          val declaring = method.declaring
          val byMethod = declMethods[declaring.name]?.get(sign)
                         ?: throw IllegalAspectDeclaringException("No such method declared in aspect class ${declaring.name}.")

          val invokeBridge = methodMapping[sign]!![declaring.name]!!

          if(methodUsing.using == Using.REPLACE && byMethod.instructions.any { insn ->
              insn is MethodInsnNode
              && insn.opcode == Opcodes.INVOKESPECIAL
              && aspectDecl.contains(ClassName.byInternalName(insn.owner))
          }) throw IllegalAspectDeclaringException("Method ${method.name} in aspect declare is not allowed to call super method of aspect declaring classes when using REPLACE strategy.")

          val methodVisitor = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            sign.methodName,
            sign.jvmDescriptor(),
            null,
            null
          )
          methodVisitor.visitCode()
          methodVisitor.invokeMethod(
            Opcodes.INVOKESPECIAL,
            thisClass,
            invokeBridge,
            false
          )
          methodVisitor.returnValue(sign.returnType)
          methodVisitor.visitMaxs(0, 0)
          methodVisitor.visitEnd()
        }
      }
    }

    // Generate non aspect methods
    methods.forEach { (sign, list) ->
      list.forEach { method ->
        val declaring = method.declaring
        val realSign = methodMapping[sign]!![declaring.name]!!

        val byMethod = declMethods[declaring.name]?.get(sign)
                       ?: throw IllegalAspectDeclaringException("No such method declared in aspect class ${declaring.name}.")

        val methodVisitor = cw.visitMethod(
          method.flags,
          realSign.methodName,
          realSign.jvmDescriptor(),
          byMethod.signature,
          byMethod.exceptions.toTypedArray()
        )
        methodVisitor.visitCode()
        methodVisitor.visitMethodBy(
          byMethod,
          thisClass, superClass, aspectDecl,
          fieldMapping, methodMapping, stubAttaches
        )
        methodVisitor.visitMaxs(0, 0)
        methodVisitor.visitEnd()
      }
    }

    cw.visitEnd()

    return cw.toByteArray()
  }

  override fun loadClass(
    loader: BytecodeLoader,
    className: ClassName,
    bytecode: ByteArray,
  ): Class<*> {
    val name = className.name

    loader.declareClass(
      name,
      bytecode
    )
    return loader.loadClass(name)
  }

  override fun checkAspectable(sourceClass: ClassDecl<*>, aspectClasses: List<ClassDecl<*>>) {
    // Check sourceClass accessible
    if (sourceClass.flags.let {
      Modifier.isFinal(it) || Modifier.isPrivate(it)
    }) throw IllegalArgumentException("Source class ${sourceClass.name} must not be final or private")
  }

  private fun MethodVisitor.returnValue(returnType: ClassName) {
    when (returnType) {
      V -> visitInsn(Opcodes.RETURN)
      B, S, I, Z, C -> visitInsn(Opcodes.IRETURN)
      J -> visitInsn(Opcodes.LRETURN)
      F -> visitInsn(Opcodes.FRETURN)
      D -> visitInsn(Opcodes.DRETURN)
      else -> visitInsn(Opcodes.ARETURN)
    }
  }

  private fun MethodVisitor.invokeMethod(opcode: Int, owner: ClassName, method: MethodSignature, isInterface: Boolean) {
    visitVarInsn(Opcodes.ALOAD, 0)
    method.paramTypes.forEachIndexed { n, param ->
      val varIndex = n + 1
      when (param.descriptor) {
        "B", "S", "I", "Z", "C" -> visitVarInsn(Opcodes.ILOAD, varIndex)
        "J" -> visitVarInsn(Opcodes.LLOAD, varIndex)
        "F" -> visitVarInsn(Opcodes.FLOAD, varIndex)
        "D" -> visitVarInsn(Opcodes.DLOAD, varIndex)
        else -> visitVarInsn(Opcodes.ALOAD, varIndex)
      }
    }
    visitMethodInsn(
      opcode,
      owner.internalName,
      method.methodName,
      method.jvmDescriptor(),
      isInterface,
    )
  }

  private fun MethodVisitor.visitMethodBy(
    byMethod: MethodNode,
    thisClass: ClassName,
    superClass: ClassName,
    aspectDecl: List<ClassName>,
    fieldMapping: Map<String, Map<ClassName, String>>,
    methodMapping: Map<MethodSignature, Map<ClassName, MethodSignature>>,
    stubAttaches: Map<ClassName, ClassName>,
  ) {
    val write = this
    val aspectDeclSet = aspectDecl.map { it.internalName }.toSet()

    val swap = object: MethodVisitor(Opcodes.ASM9, this){
      override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
      ) {
        var realOwner: ClassName = ClassName.byInternalName(owner)
        var realMethod = MethodSignature.parse(name, descriptor)
        var realInterface = isInterface

        if (name == "<init>" && aspectDeclSet.contains(owner)) {
          throw IllegalAspectDeclaringException("Constructor ${realOwner.name}.${realMethod.methodName}${realMethod.jvmDescriptor()} in aspect declare is not allowed to be called in aspect method.")
        }

        if (opcode == Opcodes.INVOKESPECIAL || opcode == Opcodes.INVOKESTATIC){
          val stubAttache = stubAttaches[realOwner]?.let { if (it == ClassName.jNothing) superClass else it }
          if (stubAttache != null) {
            realOwner = stubAttache
            realInterface = false
          }

          val mapping = methodMapping[realMethod]?.get(realOwner)
          if (mapping != null) {
            realMethod = mapping
            realOwner = thisClass
            realInterface = false
          }
        }
        else if (aspectDeclSet.contains(owner)) {
          realOwner = thisClass
          realInterface = false
        }

        write.visitMethodInsn(
          opcode,
          realOwner.internalName,
          realMethod.methodName,
          realMethod.jvmDescriptor(),
          realInterface
        )
      }

      override fun visitFieldInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String
      ) {
        var realOwner = ClassName.byInternalName(owner)
        var realField = name

        val stubAttache = stubAttaches[realOwner]?.let { if (it == ClassName.jNothing) superClass else it }
        if (stubAttache != null) {
          realOwner = stubAttache
        }

        val mapping = fieldMapping[name]?.get(realOwner)
        if (mapping != null) {
          realField = mapping
          realOwner = thisClass
        }

        if (aspectDeclSet.contains(owner)) {
          realOwner = thisClass
        }

        write.visitFieldInsn(
          opcode,
          realOwner.internalName,
          realField,
          descriptor
        )
      }

      override fun visitTypeInsn(
        opcode: Int,
        type: String
      ) {
        var realType = type
        if (aspectDeclSet.contains(type)) { realType = thisClass.internalName }
        write.visitTypeInsn(opcode, realType)
      }
    }
    byMethod.accept(swap)
  }
}