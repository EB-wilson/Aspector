package aspector.classes

import aspector.Using

open class EAspectMethod(
  declaring: ClassDecl<*>,
  methodName: String,
  parameters: List<Parameter>,
  returnType: AnnotatedType<*>,
  flag: Int,
  val using: Using,
  annotations : List<EAnnotation>,
): EMethod(
  declaring,
  methodName,
  parameters,
  returnType,
  flag,
  annotations,
)