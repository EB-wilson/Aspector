package aspector.classes

import java.lang.reflect.Modifier

interface ClassAccessor {
  companion object {
    val voidDecl: ClassDecl<Void> = PrimitiveClassDecl(Void.TYPE)
    val byteDecl: ClassDecl<Byte> = PrimitiveClassDecl(Byte::class.java)
    val shortDecl: ClassDecl<Short> = PrimitiveClassDecl(Short::class.java)
    val intDecl: ClassDecl<Int> = PrimitiveClassDecl(Int::class.java)
    val longDecl: ClassDecl<Long> = PrimitiveClassDecl(Long::class.java)
    val floatDecl: ClassDecl<Float> = PrimitiveClassDecl(Float::class.java)
    val doubleDecl: ClassDecl<Double> = PrimitiveClassDecl(Double::class.java)
    val charDecl: ClassDecl<Char> = PrimitiveClassDecl(Char::class.java)
    val booleanDecl: ClassDecl<Boolean> = PrimitiveClassDecl(Boolean::class.java)
  }

  fun <T: Any> getClassDecl(className: ClassName): ClassDecl<T>
  fun getBytes(className: ClassName): ByteArray

  private class PrimitiveClassDecl<T: Any>(
    clazz: Class<T>,
  ): ClassDecl<T>(ClassName.byClass(clazz)){
    override val flags: Int = Modifier.PUBLIC or Modifier.FINAL
    override val superClass: ClassDecl<*>? = null
    override val annotatedSuperClass: AnnotatedType<*>? = null
    override val interfaces: List<ClassDecl<*>> = emptyList()
    override val annotatedInterfaces: List<AnnotatedType<*>> = emptyList()
    override val fields: List<EField> = emptyList()
    override val constructors: List<EConstructor<T>> = emptyList()
    override val methods: List<EMethod> = emptyList()
    override val annotations: List<EAnnotation> get() = emptyList()
  }
}