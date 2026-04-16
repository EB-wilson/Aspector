package aspector.classes

abstract class AnnotationValue<T: Any, RT: Any>(
  val name: String,
) {
  abstract fun getType(): ClassName

  /**Will load some class or instance some object.
   *
   * Shouldn't be call most of the time when a pure compile-time or mixin environment.*/
  abstract val value: T

  /**Get the annotation value raw declare, use pure compile indicated.
   *
   * Don't need to load target class. use for mixin or compile plugin.*/
  abstract val rawValue: RT
}

class Value<T: Any>(
  name: String,
  override val value: T
): AnnotationValue<T, T>(name) {
  override fun getType(): ClassName = ClassName.jClassName
  override val rawValue: T = value
}

class TypeValue(
  name: String,
  val className: ClassName
): AnnotationValue<Class<*>, ClassName>(name) {
  override fun getType(): ClassName = ClassName.jClassName
  override val value: Class<*> get() = Class.forName(className.name)
  override val rawValue: ClassName = className
}

class EnumValue<T: Enum<T>>(
  name: String,
  val enumClassName: ClassName,
  val enumConstName: String
): AnnotationValue<T, Pair<ClassName, String>>(name) {
  override fun getType(): ClassName = enumClassName

  @Suppress("UNCHECKED_CAST")
  override val value: T
    get() = Class.forName(enumClassName.name)
              .enumConstants
              .first { (it as Enum<*>).name == enumConstName } as? T
            ?: throw NoSuchElementException(enumConstName)
  override val rawValue: Pair<ClassName, String> = enumClassName to enumConstName
}
