package aspector.classes.elements

import aspector.classes.AnnotatedType

class Parameter(
  val name: String,
  val annotatedType: AnnotatedType<*>,
  val annotations: List<Annotation>
){
  val type get() = annotatedType.type
}