package aspector.accesses

import aspector.annotations.PackageAccessor
import aspector.generate.ClassMaker.Companion.asName
import aspector.classes.ClassAccessor
import aspector.classes.ClassName
import aspector.classes.ClassElement
import aspector.classes.EConstructor
import aspector.classes.EMethod
import java.io.File
import java.lang.reflect.Modifier

abstract class PackageAccessHandler(
  protected val classAccessor: ClassAccessor,
) {
  open fun genPackageAccessClassName(
    accessTarget: Class<*>,
  ) = accessTarget.name + $$"$PackageAccess"

  @Suppress("UNCHECKED_CAST")
  open fun <T : Any> getPackageAccessClass(
    accessTarget: Class<T>,
  ): Class<T> {
    if (accessTarget.getAnnotation(PackageAccessor::class.java) != null) return accessTarget

    checkAccessible(accessTarget)

    val name = ClassName.byName(genPackageAccessClassName(accessTarget))

    try {
      return Class.forName(name.name) as Class<T>
    } catch (e: ClassNotFoundException) {
      val targetName = accessTarget.asName()
      val targetDecl = classAccessor.getClassDecl<T>(targetName)

      val builder = AccessBuilder(
        name,
        targetName,
      ).apply {
        targetDecl.methods
          .filter { it.flags and (Modifier.PUBLIC or Modifier.PROTECTED or Modifier.PRIVATE or Modifier.STATIC or Modifier.FINAL) == 0 }
          .forEach { method -> registerEnhanceMethod(method) }
        targetDecl.constructors
          .filter { it.flags and (Modifier.PRIVATE or Modifier.FINAL) == 0 }
          .forEach { constructor -> registerEnhanceConstructor(constructor) }
      }

      val bytecode = genPackageAccessClass(builder)

      File("${name.simpleName}.class").outputStream().write(bytecode)

      return loadClass(
        name,
        bytecode,
        accessTarget
      ) as Class<T>
    }
  }

  protected open fun <T : Any> checkAccessible(accessTarget: Class<T>) {
    if (accessTarget.isPrimitive)
      throw IllegalArgumentException("Cannot enhance a primitive type.")
    if (accessTarget.isInterface)
      throw IllegalArgumentException("Cannot enhance an interface type: $accessTarget.")
    if (Modifier.isFinal(accessTarget.modifiers) || Modifier.isPrivate(accessTarget.modifiers))
      throw IllegalArgumentException("Cannot enhance access class with modifiers final or private.")
  }

  protected abstract fun genPackageAccessClass(
    builder: AccessBuilder
  ): ByteArray

  protected abstract fun loadClass(
    className: ClassName,
    bytecode: ByteArray,
    accessTarget: Class<*>,
  ): Class<*>

  class AccessBuilder  (
    val className: ClassName,
    val accessTarget: ClassName,
  ) {
    val enhanceElements: MutableList<ClassElement> = mutableListOf()

    fun registerEnhanceMethod(method: EMethod) = enhanceElements.add(method)
    fun registerEnhanceConstructor(constructor: EConstructor<*>) = enhanceElements.add(constructor)
  }
}