import aspector.RuntimeAspector
import aspector.Using
import aspector.accesses.UnsafePackageAccessHandler
import aspector.annotations.AspectElement
import aspector.annotations.Stub
import aspector.classes.BytecodeClassLoader
import aspector.generate.AspectMaker
import kotlin.reflect.KClass

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestAnno(
  val str: String,
  val type: KClass<*>,
  val arrayTypes: Array<KClass<*>>,
  val arrayEnum: Array<Using>,
  val intArray: IntArray,
)

interface Aspect {
  fun definePackage(c: Class<*>): Package
}

interface AccessStub {
  fun definePackage(c: Class<*>): Package { TODO() }
}

class LoaderAspect:
    @Stub ClassLoader(),
    @Stub AccessStub,
    Aspect
{
  @AspectElement(Using.OVERRIDE)
  override fun definePackage(
    c: @TestAnno(
      "test text",
      Aspect::class,
      [Aspect::class, AccessStub::class],
      [Using.AFTER_RETURN, Using.OVERRIDE],
      [12, 25, 74, 1]
    ) Class<*>
  ): Package{
    println("definePackage: $c")
    return super<AccessStub>.definePackage(c)
  }
}

fun main() {
  val loader = BytecodeClassLoader(RuntimeAspector::class.java.classLoader)

  RuntimeAspector.withMaker(
    AspectMaker.factory(),
    UnsafePackageAccessHandler.factory()
  ){
    use(loader)
    val inst = LoaderAspect::class
      .applyOn(ClassLoader::class.open())
      .instance()

    val instAsp = inst as Aspect
    instAsp.definePackage(Object::class.java)
  }

}
