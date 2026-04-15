import aspector.RuntimeAspector
import aspector.accesses.UnsafePackageAccessHandler
import aspector.annotations.Stub
import aspector.classes.BytecodeClassLoader
import aspector.generate.AspectMaker

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
  override fun definePackage(c: Class<*>): Package{
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
