package aspector.classes

import aspector.classes.elements.EConstructor
import aspector.classes.elements.EField
import aspector.classes.elements.EMethod
import java.lang.reflect.Modifier

abstract class ClassDecl<T: Any>(
  val name: ClassName,
  val flags: Int,
) {
  companion object {
    const val BRIDGE: Int = 0x00000040
    const val VARARGS: Int = 0x00000080
    const val SYNTHETIC: Int = 0x00001000
    const val ANNOTATION: Int = 0x00002000
    const val ENUM: Int = 0x00004000
    const val MANDATED: Int = 0x00008000
  }

  abstract val superClass: ClassDecl<*>?
  abstract val annotatedSuperClass: AnnotatedType<*>?
  abstract val interfaces: List<ClassDecl<*>>
  abstract val annotatedInterfaces: List<AnnotatedType<*>>

  abstract val fields: List<EField>
  abstract val constructors: List<EConstructor<T>>
  abstract val methods: List<EMethod>

  val isPublic get() = Modifier.isPublic(flags)
  val isProtected get() = Modifier.isProtected(flags)
  val isPrivate get() = Modifier.isPrivate(flags)

  val isInterface get() = Modifier.isInterface(flags)
  val isAbstract get() = Modifier.isAbstract(flags)
  val isFinal get() = Modifier.isFinal(flags)

  val isPrimitive get() = name.isPrimitive
  val isArray get() = name.isArray
  val isEnum get() = (flags and ENUM) != 0 && superClass?.name == ClassName.by(Enum::class.java)
}