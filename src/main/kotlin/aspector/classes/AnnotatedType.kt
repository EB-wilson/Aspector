package aspector.classes

class AnnotatedType<T : Any>(
  val type: ClassDecl<T>,
  val annotations: List<EAnnotation>
) {
  @Suppress("UNCHECKED_CAST")
  fun getAnnotation(annoTypeName: ClassName) =
    annotations.find { it.type == annoTypeName }
}