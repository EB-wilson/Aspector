package aspector.annotations

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AspectExtends(
  vararg val extends: KClass<*>,
)
