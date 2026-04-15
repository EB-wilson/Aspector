package aspector.classes

class AnnotatedType<T : Any>(
  val type: ClassDecl<T>,
  val annotations: List<Annotation>
) {
  @Suppress("UNCHECKED_CAST")
  fun <T: Annotation> getAnnotation(annoType: Class<T>) =
    annotations.find { annoType.isAssignableFrom(it::class.java) } as? T
}