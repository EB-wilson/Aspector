package aspector.classes

open class EConstructor<T: Any>(
  declaring: ClassDecl<T>,
  val parameters: List<Parameter>,
  flag: Int,
  annotations: List<EAnnotation>,
): ClassElement(
  declaring,
  "<init>",
  flag,
  annotations,
){
  val signature by lazy {
    MethodSignature(
      "<init>",
      parameterTypes.map { it.name },
      ClassName.V
    )
  }

  val parameterTypes get() = parameters.map { it.annotatedType.type }
  val annotatedParameterTypes get() = parameters.map { it.annotatedType }
}