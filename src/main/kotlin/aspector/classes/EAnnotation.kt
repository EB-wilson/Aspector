package aspector.classes

open class EAnnotation(
  val type: ClassName,
  private val values: Map<String, AnnotationValue<*, *>>
) {
  @Suppress("UNCHECKED_CAST")
  fun <T: AnnotationValue<*, *>> getValue(name: String): T? = values[name] as? T
}