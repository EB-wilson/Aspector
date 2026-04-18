package aspector.classes

class Parameter(
  val name: String,
  val annotatedType: AnnotatedType<*>,
  val annotations: List<EAnnotation>
){
  val type get() = annotatedType.type

  @Suppress("UNCHECKED_CAST")
  fun getAnnotation(annoTypeName: ClassName) =
    annotations.find { it.type == annoTypeName }
}