package aspector.classes

import aspector.generate.ClassMaker.Companion.asName

sealed class AnnotationValue<T: Any, RT: Any>() {
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
  override val value: T
): AnnotationValue<T, T>() {
  override fun getType(): ClassName = ClassName.jClassName
  override val rawValue: T = value
}

class ArrayValue<V : Any, T: AnnotationValue<V, *>>(
  values: List<T>
): AnnotationValue<List<V>, List<T>>() {
  override fun getType(): ClassName = ClassName.jClassName.arrayName
  override val value: List<V> = values.map { it.value }
  override val rawValue: List<T> = values
}

class NestedAnnotationValue(
  override val rawValue: EAnnotation
): AnnotationValue<Annotation, EAnnotation>() {
  override fun getType(): ClassName = Annotation::class.asName()

  override val value: Annotation by lazy {
    val annoType = Class.forName(rawValue.type.name)
    java.lang.reflect.Proxy.newProxyInstance(
      annoType.classLoader,
      arrayOf(annoType)
    ) { obj, method, args ->
      rawValue.getValue<AnnotationValue<*, *>>(method.name)?.value?: method.invoke(obj, *(args?: emptyArray()))
    } as Annotation
  }
}

class TypeValue(
  val className: ClassName
): AnnotationValue<Class<*>, ClassName>() {
  override fun getType(): ClassName = ClassName.jClassName
  override val value: Class<*> get() = Class.forName(className.name)
  override val rawValue: ClassName = className
}

class EnumValue<T: Enum<T>>(
  val enumClassName: ClassName,
  val enumConstName: String
): AnnotationValue<T, Pair<ClassName, String>>() {
  override fun getType(): ClassName = enumClassName

  @Suppress("UNCHECKED_CAST")
  override val value: T
    get() = Class.forName(enumClassName.name)
      .enumConstants
      .first { (it as Enum<*>).name == enumConstName } as? T
    ?: throw NoSuchElementException(enumConstName)
  override val rawValue: Pair<ClassName, String> = enumClassName to enumConstName
}
