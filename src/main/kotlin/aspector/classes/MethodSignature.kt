package aspector.classes

data class MethodSignature(
  val methodName: String,
  val paramTypes: List<ClassName>,
  val returnType: ClassName,
){
  companion object{
    private val namedDescriptorMatcher = Regex("^[^.;()]+\\(([BCDFIJSZ]|\\[+[BCDFIJSZ]|\\[*L[^;]+;)*\\)([BCDFIJSZ]|\\[+[BCDFIJSZ]|V|\\[*L[^;]+;)$")
    private val descriptorMatcher = Regex("^\\(([BCDFIJSZ]|\\[+[BCDFIJSZ]|\\[*L[^;]+;)*\\)([BCDFIJSZ]|\\[+[BCDFIJSZ]|V|\\[*L[^;]+;)$")

    fun parse(methodName: String, descriptor: String): MethodSignature{
      if (!descriptorMatcher.matches(descriptor))
        throw IllegalArgumentException("Invalid signature string: $descriptor")
      if (methodName.contains("(") || methodName.contains(")") || methodName.contains(";"))
        throw IllegalArgumentException("Invalid method name: $methodName")

      return parse(methodName + descriptor)
    }

    fun parse(descriptor: String): MethodSignature{
      if (!namedDescriptorMatcher.matches(descriptor))
        throw IllegalArgumentException("Invalid signature string: $descriptor")

      val builder = StringBuilder()

      var name = ""
      var ret = false
      val paramTypes = mutableListOf<ClassName>()
      var returnType: ClassName? = null

      descriptor.forEach { c ->
        when(c){
          '(' -> {
            name = builder.toString()
            builder.clear()
          }
          ';' -> {
            builder.append(c)

            if (ret) {
              returnType = ClassName.byDescriptor(builder.toString())
              builder.clear()
            }
            else {
              paramTypes.add(ClassName.byDescriptor(builder.toString()))
              builder.clear()
            }
          }
          ')' -> {
            builder.clear()
            ret = true
          }
          else -> builder.append(c)
        }
      }

      returnType = returnType?: ClassName.byDescriptor(builder.toString())
      return MethodSignature(
        name,
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