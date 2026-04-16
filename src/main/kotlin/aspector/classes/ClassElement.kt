package aspector.classes

abstract class ClassElement(
  val declaring: ClassDecl<*>,
  val name: String,
  val flags: Int,
  val annotations: List<EAnnotation>,
){
  @Suppress("UNCHECKED_CAST")
  fun getAnnotation(annoTypeName: ClassName) =
    annotations.find { it.annotationType == annoTypeName }
}