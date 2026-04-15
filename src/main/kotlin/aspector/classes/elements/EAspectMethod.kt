package aspector.classes.elements

import aspector.Using
import aspector.classes.AnnotatedType
import aspector.classes.ClassDecl

open class EAspectMethod(
  declaring: ClassDecl<*>,
  methodName: String,
  parameters: List<Parameter>,
  returnType: AnnotatedType<*>,
  flag: Int,
  val using: Using,
  annotations : List<Annotation>,
): EMethod(
  declaring,
  methodName,
  parameters,
  returnType,
  flag,
  annotations,
)