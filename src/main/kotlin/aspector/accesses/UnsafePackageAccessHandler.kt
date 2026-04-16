package aspector.accesses

import aspector.annotations.PackageAccessor
import aspector.generate.ClassMaker.Companion.asName
import aspector.classes.ClassAccessor
import aspector.classes.ClassName
import aspector.classes.ClassName.Companion.B
import aspector.classes.ClassName.Companion.C
import aspector.classes.ClassName.Companion.D
import aspector.classes.ClassName.Companion.F
import aspector.classes.ClassName.Companion.I
import aspector.classes.ClassName.Companion.J
import aspector.classes.ClassName.Companion.S
import aspector.classes.ClassName.Companion.V
import aspector.classes.ClassName.Companion.Z
import aspector.classes.MethodSignature
import aspector.classes.EConstructor
import aspector.classes.EMethod
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import sun.misc.Unsafe
import java.lang.reflect.Method
import java.security.ProtectionDomain

class UnsafePackageAccessHandler private constructor(
  classAccessor: ClassAccessor
): PackageAccessHandler(classAccessor) {
  companion object {
    private var unsafeInstance: Any
    private var unsafeDefineClass: Method

    val packageAccessorT = PackageAccessor::class.java.asName()

    fun create(classAccessor: ClassAccessor) = UnsafePackageAccessHandler(classAccessor)
    fun factory(): (ClassAccessor) -> UnsafePackageAccessHandler = { UnsafePackageAccessHandler(it) }

    init {
      try {
        val sumUnsafe = Unsafe::class.java
        unsafeDefineClass = sumUnsafe.getMethod(
          "defineClass",
          String::class.java,
          ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
          ClassLoader::class.java, ProtectionDomain::class.java
        ).also { it.isAccessible = true }
        unsafeInstance = sumUnsafe
          .getDeclaredConstructor()
          .also { it.isAccessible = true }
          .newInstance()
      } catch (_: Throwable) {
        Demodulator.setup()
        Demodulator.makeModuleOpen(
          Object::class.java.module,
          "jdk.internal.misc",
          UnsafePackageAccessHandler::class.java.module
        )
        val internalUnsafe = Class.forName("jdk.internal.misc.Unsafe")
        unsafeDefineClass = internalUnsafe.getMethod(
          "defineClass",
          String::class.java,
          ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
          ClassLoader::class.java, ProtectionDomain::class.java
        ).also { it.isAccessible = true }
        unsafeInstance = internalUnsafe
          .getDeclaredConstructor()
          .also { it.isAccessible = true }
          .newInstance()
      }
    }
  }

  override fun genPackageAccessClass(builder: AccessBuilder): ByteArray {
    val className = builder.className
    val targetName = builder.accessTarget

    val elements = builder.enhanceElements
    val methods = elements.filterIsInstance<EMethod>()
    val constructors = elements.filterIsInstance<EConstructor<*>>()

    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(
      Opcodes.V1_8,
      Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
      className.internalName,
      null,
      targetName.internalName,
      null
    )
    classWriter.visitAnnotation(
      packageAccessorT.descriptor,
      true
    ).visitEnd()

    methods.forEach {
      val methodVisitor = classWriter.visitMethod(
        it.flags and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED).inv() or Opcodes.ACC_PROTECTED,
        it.name,
        it.descriptor.jvmDescriptor(),
        null,
        null,
      )
      methodVisitor.visitCode()

      methodVisitor.invokeMethod(
        targetName,
        it.descriptor,
        false
      )
      methodVisitor.returnValue(it)

      methodVisitor.visitMaxs(0, 0)
      methodVisitor.visitEnd()
    }

    constructors.forEach {
      val methodVisitor = classWriter.visitMethod(
        it.flags and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED).inv() or Opcodes.ACC_PROTECTED,
        "<init>",
        it.descriptor.jvmDescriptor(),
        null,
        null
      )
      methodVisitor.visitCode()

      methodVisitor.invokeMethod(
        targetName,
        it.descriptor,
        false
      )
      methodVisitor.visitInsn(Opcodes.RETURN)

      methodVisitor.visitMaxs(0, 0)
      methodVisitor.visitEnd()
    }
    classWriter.visitEnd()

    return classWriter.toByteArray()
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

  private fun MethodVisitor.invokeMethod(owner: ClassName, method: MethodSignature, isInterface: Boolean) {
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
      Opcodes.INVOKESPECIAL,
      owner.internalName,
      method.methodName,
      method.jvmDescriptor(),
      isInterface,
    )
  }

  override fun loadClass(
    className: ClassName,
    bytecode: ByteArray,
    accessTarget: Class<*>,
  ): Class<*> {
    val accessTargetDomain = accessTarget.protectionDomain
    return unsafeDefineClass.invoke(
      unsafeInstance,
      className.name,
      bytecode, 0, bytecode.size,
      accessTarget.classLoader, accessTargetDomain
    ) as Class<*>
  }
}