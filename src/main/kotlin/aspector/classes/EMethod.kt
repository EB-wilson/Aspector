package aspector.classes

open class EMethod(
  declaring: ClassDecl<*>,
  name: String,
  val parameters: List<Parameter>,
  val annotatedReturnType: AnnotatedType<*>,
  accessFlag: Int,
  annotations: List<EAnnotation>,
): ClassElement(
  declaring,
  name,
  accessFlag,
  annotations
){
  val signature by lazy {
    MethodSignature(
      name,
      parameterTypes.map { it.name },
      returnType.name
    )
  }

  val parameterTypes get() = parameters.map { it.annotatedType.type }
  val annotatedParameterTypes get() = parameters.map { it.annotatedType }
  val returnType get() = annotatedReturnType.type
}