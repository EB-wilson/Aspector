package aspector.classes

data class MethodSignature(
  val methodName: String,
  val paramTypes: List<ClassName>,
  val returnType: ClassName,
){
  companion object{
    private val descriptorMatcher = Regex("^\\(([BCDFIJSZ]|\\[+[BCDFIJSZ]|\\[*L[^;]+;)*\\)([BCDFIJSZ]|\\[+[BCDFIJSZ]|V|\\[*L[^;]+;)$")

    fun parse(methodName: String, descriptor: String): MethodSignature{
      if (!descriptorMatcher.matches(descriptor))
        throw IllegalArgumentException("Invalid signature string: $descriptor")
      if (methodName.contains("(") || methodName.contains(")") || methodName.contains(";"))
        throw IllegalArgumentException("Invalid method name: $methodName")

      val paramTypes = mutableListOf<ClassName>()
      var returnType: ClassName? = null

      var index = 0
      fun readNextType(): ClassName {
        val res = when(val c = descriptor[index]) {
          'B' -> ClassName.B
          'S' -> ClassName.S
          'I' -> ClassName.I
          'J' -> ClassName.J
          'F' -> ClassName.F
          'D' -> ClassName.D
          'C' -> ClassName.C
          'Z' -> ClassName.Z
          'V' -> ClassName.V
          'L' -> {
            val nextSemicolon = descriptor.indexOf(';', index)
            if (nextSemicolon == -1) throw IllegalArgumentException("Invalid signature string: $descriptor")

            val className = ClassName.byDescriptor(descriptor.substring(index, nextSemicolon + 1))
            index = nextSemicolon
            className
          }
          else -> throw IllegalArgumentException("Invalid signature string: $c")
        }

        index++
        return res
      }

      while (index < descriptor.length) {
        val c = descriptor[index]
        when(c) {
          '(' -> index++
          ')' -> {
            index++
            returnType = readNextType()
          }
          else -> {
            paramTypes.add(readNextType())
          }
        }
      }

      if (returnType == null)
        throw IllegalArgumentException("Invalid signature string: $descriptor")

      return MethodSignature(
        methodName,
        paramTypes,
        returnType
      )
    }
  }

  fun match(other: MethodSignature): Boolean{
    if (other === this) return true

    if (methodName != other.methodName) return false
    if (paramTypes != other.paramTypes) return false

    return true
  }

  private var hash = -1

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    if (other !is MethodSignature) return false

    if (methodName != other.methodName) return false
    if (paramTypes != other.paramTypes) return false
    if (returnType != other.returnType) return false

    return true
  }

  override fun hashCode(): Int {
    if (hash != -1) return hash

    var result = methodName.hashCode()
    result = 31*result + paramTypes.hashCode()
    result = 31*result + returnType.hashCode()
    hash = result

    return result
  }

  fun jvmDescriptor() = "(${StringBuilder().also { b -> paramTypes.forEach { b.append(it.descriptor) } }})${returnType.descriptor}"
}