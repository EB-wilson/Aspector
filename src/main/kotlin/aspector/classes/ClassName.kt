package aspector.classes

import org.objectweb.asm.Type

class ClassName(
  val signatureName: String,
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

    val jObject = by(Object::class.java)
    val jString = by(String::class.java)

    private fun signToInternal(signatureName: String): String = when(signatureName.first()) {
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
        signToInternal(signatureName.substring(1))
      }
      else -> throw IllegalArgumentException("Illegal class name: $signatureName")
    }
    private fun internalToSign(internalName: String): String = when (internalName) {
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

    fun by(clazz: Class<*>) = ClassName(internalToSign(Type.getInternalName(clazz)))
    fun byInternalName(internalName: String) = ClassName(internalToSign(internalName))
    fun byName(className: String) = ClassName(internalToSign(className.replace(".", "/")))
  }

  val internalName: String get() = signToInternal(signatureName)
  val name: String get() = signToInternal(signatureName).replace("/", ".")
  val simpleName: String get() = name.substringAfterLast(".")
  val packageName: String get() = name.substringBeforeLast(".")

  val isPrimitive: Boolean get() = signatureName.length == 1
  val isArray: Boolean get() = signatureName.startsWith("[")

  val componentName: ClassName get() = ClassName(signatureName.substringAfter("["))
  val arrayName: ClassName get() = ClassName("[$signatureName")

  override fun toString() = name

  override fun hashCode(): Int = signatureName.hashCode()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ClassName

    return signatureName == other.signatureName
  }
}