package aspector.classes

open class EField(
  declaring: ClassDecl<*>,
  name: String,
  val annotatedType: AnnotatedType<*>,
  flag: Int,
  val constant: Any?,
  annotations: List<EAnnotation>,
): ClassElement(
  declaring,
  name,
  flag,
  annotations,
){
  val type get() = annotatedType.type
}