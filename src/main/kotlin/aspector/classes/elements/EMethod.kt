package aspector.classes.elements

import aspector.classes.AnnotatedType
import aspector.classes.ClassDecl
import aspector.classes.MethodSignature

open class EMethod(
  declaring: ClassDecl<*>,
  name: String,
  val parameters: List<Parameter>,
  val annotatedReturnType: AnnotatedType<*>,
  accessFlag: Int,
  annotations: List<Annotation>,
): ClassElement(
  declaring,
  name,
  accessFlag,
  annotations
){
  val descriptor by lazy {
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