package aspector.annotations

import aspector.Using

@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
)
annotation class AspectElement(
  val using: Using = Using.OVERRIDE,
)
