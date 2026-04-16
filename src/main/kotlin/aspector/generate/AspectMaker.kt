package aspector.generate

import aspector.Using.*
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
import aspector.classes.EConstructor
import aspector.classes.EField
import aspector.classes.EMethod
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File

class AspectMaker private constructor(
  classAccessor: ClassAccessor,
): ClassMaker(classAccessor){
  companion object {
    @JvmStatic
    fun create(
      accessor: ClassAccessor
    ): AspectMaker = AspectMaker(accessor)

    @JvmStatic
    fun factory(): (ClassAccessor) -> ClassMaker = { AspectMaker(it) }
  }

  override fun generateClassName(aspectImpl: ClassDecl<*>, targetClass: ClassDecl<*>) = ClassName.byName(
    "${targetClass.name.name.let { 
      if (it.startsWith("java.")) it.replace("java.", "javas.")
      else it
    } }$${aspectImpl.name.simpleName}@${aspectImpl.name.hashCode().toHexString()}"
  )

  override fun generateBytecode(builder: AspectBuilder): ByteArray {
    val thisClass = builder.className
    val accessFlags = builder.accessFlags
    val stubs = builder.stubTypes.toSet()
    val superClass = builder.superClass
    val aspectDecl = builder.aspectDecl
    val implements = builder.interfaces

    val declBytes = bytesAccessor.getBytes(aspectDecl)

    val elements = builder.implElements
    val aspectElements = builder.aspectElements

    val fields = elements.filterIsInstance<EField>()
    val methods = elements.filterIsInstance<EMethod>()
    val constructors = elements.filterIsInstance<EConstructor<*>>()

    val cr = ClassReader(declBytes)
    val implRoot = ClassNode(Opcodes.ASM9)
    cr.accept(implRoot, ClassReader.SKIP_DEBUG)

    val implMethods = implRoot.methods
      .associateBy { MethodSignature.parse(it.name, it.desc) }

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

    fields.forEach {
      val fieldVisitor = cw.visitField(
        it.flags,
        it.name,
        it.type.name.descriptor,
        null,
        it.constant
      )
      fieldVisitor.visitEnd()
    }

    methods.forEach {
      val methodVisitor = cw.visitMethod(
        it.flags,
        it.name,
        it.descriptor.jvmDescriptor(),
        null,
        null,
      )

      val byMethod = implMethods[it.descriptor] ?:
                     throw AspectDeclaringException("Method ${it.name} not found in aspect implementation")

      methodVisitor.visitCode()
      methodVisitor.visitMethodBy(
        byMethod,
        thisClass,
        superClass,
        aspectDecl,
        stubs
      )

      methodVisitor.visitEnd()
    }

    constructors.forEach {
      val methodVisitor = cw.visitMethod(
        it.flags,
        "<init>",
        it.descriptor.jvmDescriptor(),
        null,
        null,
      )

      val byMethod = implMethods[it.descriptor] ?: run{
        if (it.descriptor.paramTypes.isEmpty()) {
          methodVisitor.visitCode()
          methodVisitor.invokeMethod(
            Opcodes.INVOKESPECIAL,
            superClass,
            it.descriptor,
            false
          )
          methodVisitor.visitMaxs(0, 0)
          methodVisitor.visitEnd()

          return@forEach
        }
        else throw AspectDeclaringException("Method ${it.name} not found in aspect implementation")
      }

      methodVisitor.visitCode()
      methodVisitor.visitMethodBy(
        byMethod,
        thisClass,
        superClass,
        aspectDecl,
        stubs
      )
      methodVisitor.visitInsn(Opcodes.RETURN)

      methodVisitor.visitMaxs(0, 0)
      methodVisitor.visitEnd()
    }

    aspectElements.forEach {
      val byMethod = implMethods[it.descriptor]
      byMethod?.also { m ->
        val bridge = when(it.using) {
          OVERRIDE -> MethodSignature("NONE", emptyList(), V)
          else -> cw.buildBridgeMethod(
            m,
            thisClass,
            superClass,
            aspectDecl,
            stubs
          )
        }

        val methodVisitor = cw.visitMethod(
          if (it.using != OVERRIDE) it.flags and (Opcodes.ACC_BRIDGE.inv()) else it.flags,
          it.name,
          it.descriptor.jvmDescriptor(),
          null,
          null,
        )
        methodVisitor.visitCode()

        when(it.using) {
          BEFORE, BEFORE_RETURN -> {
            methodVisitor.invokeMethod(Opcodes.INVOKESPECIAL, thisClass, bridge, false)
            if (it.using == BEFORE && it.descriptor.returnType != V) methodVisitor.visitInsn(Opcodes.POP)
            methodVisitor.invokeMethod(Opcodes.INVOKESPECIAL, superClass, it.descriptor, false)
            if (it.using == BEFORE_RETURN && it.descriptor.returnType != V) methodVisitor.visitInsn(Opcodes.POP)

            methodVisitor.returnValue(it)
          }
          OVERRIDE -> {
            methodVisitor.visitMethodBy(
              m,
              thisClass,
              superClass,
              aspectDecl,
              stubs
            )
          }
          AFTER, AFTER_RETURN -> {
            methodVisitor.invokeMethod(Opcodes.INVOKESPECIAL, superClass, it.descriptor, false)
            if (it.using == AFTER_RETURN && it.descriptor.returnType != V) methodVisitor.visitInsn(Opcodes.POP)
            methodVisitor.invokeMethod(Opcodes.INVOKESPECIAL, thisClass, bridge, false)
            if (it.using == AFTER && it.descriptor.returnType != V) methodVisitor.visitInsn(Opcodes.POP)

            methodVisitor.returnValue(it)
          }
        }

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

    File("${className.simpleName}.class").outputStream().write(bytecode)

    loader.declareClass(
      name,
      bytecode
    )
    return loader.loadClass(name)
  }

  private fun MethodVisitor.returnValue(method: EMethod) {
    when (method.descriptor.returnType) {
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

  private fun ClassVisitor.buildBridgeMethod(
    byMethod: MethodNode,
    thisClass: ClassName,
    superClass: ClassName,
    aspectImpl: ClassName,
    stubSpec: Set<ClassName>,
  ): MethodSignature {
    val methodSignature = MethodSignature.parse(
      byMethod.name + $$"$bridge",
      byMethod.desc
    )
    val methodVisitor = visitMethod(
      Opcodes.ACC_PRIVATE or Opcodes.ACC_BRIDGE,
      methodSignature.methodName,
      byMethod.desc,
      byMethod.signature,
      byMethod.exceptions.toTypedArray()
    )
    methodVisitor.visitCode()
    methodVisitor.visitMethodBy(
      byMethod,
      thisClass,
      superClass,
      aspectImpl,
      stubSpec
    )
    methodVisitor.visitMaxs(0, 0)
    methodVisitor.visitEnd()

    return methodSignature
  }

  private fun MethodVisitor.visitMethodBy(
    byMethod: MethodNode,
    thisClass: ClassName,
    superClass: ClassName,
    aspectImpl: ClassName,
    stubSpec: Set<ClassName>
  ) {
    val write = this

    val swap = object: MethodVisitor(Opcodes.ASM9, this){
      override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
      ) {
        var realOwner = owner
        var realInterface = isInterface
        if (stubSpec.contains(ClassName.byInternalName(owner)) && opcode == Opcodes.INVOKESPECIAL){
          realOwner = superClass.internalName
          realInterface = false
        }
        if (owner == aspectImpl.internalName) {
          realOwner = thisClass.internalName
          realInterface = false
        }
        write.visitMethodInsn(
          opcode,
          realOwner,
          name,
          descriptor,
          realInterface
        )
      }

      override fun visitFieldInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String
      ) {
        var realOwner = owner
        if (stubSpec.contains(ClassName.byInternalName(owner))) { realOwner = superClass.internalName }
        if (owner == aspectImpl.internalName){ realOwner = thisClass.internalName }
        write.visitFieldInsn(opcode, realOwner, name, descriptor)
      }

      override fun visitTypeInsn(
        opcode: Int,
        type: String
      ) {
        var realType = type
        if (type == aspectImpl.internalName) { realType = thisClass.internalName }
        write.visitTypeInsn(opcode, realType)
      }
    }
    byMethod.accept(swap)
  }
}