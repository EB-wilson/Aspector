package aspector

import kotlin.reflect.KClass

infix fun <T: Any> T.type(type: KClass<T>) = TypePair(type.java, this)
infix fun <T: Any> T.type(type: Class<T>) = TypePair(type, this)

data class TypePair<T : Any>(
  val type: Class<T>,
  val value: T
)