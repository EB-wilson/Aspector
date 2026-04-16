package aspector.classes

import org.objectweb.asm.Type

class ClassName private constructor(
  val descriptor: String,
) {
  companion object {
    val V = ClassName("V")
    val Z = ClassName("Z")
    val C = ClassName("C")
    val B = ClassName("B")
    val S = ClassName("S")
    val I = ClassName("I")
    val J = ClassName("J")
    val F = ClassName("F")
    val D = ClassName("D")

    val jObject = byClass(Object::class.java)
    val jString = byClass(String::class.java)
    val jClassName = byClass(ClassName::class.java)

    private fun descToInternal(signatureName: String): String = when(signatureName.first()) {
      'V' -> "void"
      'Z' -> "boolean"
      'C' -> "char"
      'B' -> "byte"
      'S' -> "short"
      'I' -> "int"
      'J' -> "long"
      'F' -> "float"
      'D' -> "double"
      'L' -> {
        signatureName.substring(1).trimEnd(';')
      }
      '[' -> {
        descToInternal(signatureName.substring(1))
      }
      else -> throw IllegalArgumentException("Illegal class name: $signatureName")
    }
    private fun internalToDesc(internalName: String): String = when (internalName) {
      "void" -> "V"
      "boolean" -> "Z"
      "char" -> "C"
      "byte" -> "B"
      "short" -> "S"
      "int" -> "I"
      "long" -> "J"
      "float" -> "F"
      "double" -> "D"
      else -> if (internalName.startsWith("[")) internalName
              else "L$internalName;"
    }

    fun byClass(clazz: Class<*>) = ClassName(Type.getDescriptor(clazz))
    fun byDescriptor(descriptor: String) = ClassName(descriptor)
    fun byInternalName(internalName: String) = ClassName(internalToDesc(internalName))
    fun byName(name: String) = ClassName(internalToDesc(name.replace(".", "/")))
  }

  val internalName: String get() = descToInternal(descriptor)
  val name: String get() = descToInternal(descriptor).replace("/", ".")
  val simpleName: String get() = name.substringAfterLast(".")
  val packageName: String get() = name.substringBeforeLast(".")

  val isPrimitive: Boolean get() = descriptor.length == 1
  val isArray: Boolean get() = descriptor.startsWith("[")

  val componentName: ClassName get() = ClassName(descriptor.substringAfter("["))
  val arrayName: ClassName get() = ClassName("[$descriptor")

  override fun toString() = name

  override fun hashCode(): Int = descriptor.hashCode()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ClassName

    return descriptor == other.descriptor
  }
}