package aspector.classes.elements

import aspector.classes.AnnotatedType
import aspector.classes.ClassDecl

open class EField(
  declaring: ClassDecl<*>,
  name: String,
  val annotatedType: AnnotatedType<*>,
  flag: Int,
  val constant: Any?,
  annotations: List<Annotation>,
): ClassElement(
  declaring,
  name,
  flag,
  annotations,
){
  val type get() = annotatedType.type
}