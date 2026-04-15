package aspector.classes.elements

import aspector.classes.ClassDecl

abstract class ClassElement(
  val declaring: ClassDecl<*>,
  val name: String,
  val flags: Int,
  val annotations: List<Annotation>,
){
  @Suppress("UNCHECKED_CAST")
  fun <T: Annotation> getAnnotation(annoType: Class<T>) =
    annotations.find { annoType.isAssignableFrom(it::class.java) } as? T
}