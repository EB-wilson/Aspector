import aspector.RuntimeAspector
import aspector.Using
import aspector.accesses.UnsafePackageAccessHandler
import aspector.annotations.AspectExtends
import aspector.annotations.Shared
import aspector.annotations.Stub
import aspector.classes.BytecodeClassLoader
import aspector.generate.ProxyAspectFactory
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

class Base1:
    @Stub ClassLoader(),
    @Stub AccessStub,
    Aspect
{
  @Shared var called = 5

  override fun definePackage(c: Class<*>): Package {
    println("definePackage with base1: $c")
    println("called: ${called++}")
    return super<AccessStub>.definePackage(c)
  }
}

class Base2:
    @Stub ClassLoader(),
    @Stub AccessStub,
    Aspect
{
  @Shared var called = 5

  override fun definePackage(c: Class<*>): Package {
    println("definePackage with base2: $c")
    println("called: ${called++}")
    return super<AccessStub>.definePackage(c)
  }
}

interface B1 { fun definePackage(c: Class<*>): Package = TODO() }
interface B2 { fun definePackage(c: Class<*>): Package = TODO() }

@AspectExtends(Base1::class, Base2::class)
class LoaderAspect:
    @Stub ClassLoader(),
    @Stub AccessStub,
    @Stub(Base1::class) B1,
    @Stub(Base2::class) B2
{
  @Shared var called = 5

  override fun definePackage(c: Class<*>): Package {
    print()

    super<B1>.definePackage(c)
    return super<B2>.definePackage(c)
  }

  private fun print(){
    println("Private method")
  }
}

fun main() {
  val loader = BytecodeClassLoader(RuntimeAspector::class.java.classLoader)

  RuntimeAspector.withMaker(
    ProxyAspectFactory.factory(),
    UnsafePackageAccessHandler.factory()
  ){
    use(loader)
    val aspectDecl = ClassLoader::class.open() apply (
        @AspectExtends(Base1::class, Base2::class)
        object:
          @Stub ClassLoader(),
          @Stub AccessStub,
          @Stub(Base1::class) B1,
          @Stub(Base2::class) B2
        {
          @Shared var called = 5

          override fun definePackage(c: Class<*>): Package {
            print()

            super<B1>.definePackage(c)
            return super<B2>.definePackage(c)
          }

          private fun print(){
            println("Private method")
          }
        }
    )::class

    val inst = aspectDecl.instance()
    val instAsp = inst as Aspect
    instAsp.definePackage(Object::class.java)
  }

}
