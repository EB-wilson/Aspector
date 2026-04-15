package aspector.classes.elements

import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.MethodSignature

open class EConstructor<T: Any>(
  declaring: ClassDecl<T>,
  val parameters: List<Parameter>,
  flag: Int,
  annotations: List<Annotation>,
): ClassElement(
  declaring,
  "<init>",
  flag,
  annotations,
){
  val descriptor by lazy {
    MethodSignature(
      "<init>",
      parameterTypes.map { it.name },
      ClassName.V
    )
  }

  val parameterTypes get() = parameters.map { it.annotatedType.type }
  val annotatedParameterTypes get() = parameters.map { it.annotatedType }
}