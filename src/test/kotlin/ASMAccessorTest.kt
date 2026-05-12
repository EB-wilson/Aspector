import aspector.classes.ASMClassAccessor
import aspector.generate.AspectFactory.Companion.asName

class ASMAccessorTest {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val accessor = ASMClassAccessor()
      val clazz = LoaderAspect::class.asName()

      val c = accessor.getClassDecl<LoaderAspect>(clazz)
      c.annotatedSuperClass
    }
  }
}
