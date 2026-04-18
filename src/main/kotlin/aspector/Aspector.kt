package aspector

import aspector.annotations.AspectElement
import aspector.annotations.Stub
import aspector.generate.AspectDeclaringException
import aspector.generate.ClassMaker
import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.EAspectMethod
import aspector.classes.EnumValue
import aspector.generate.ClassMaker.Companion.asName
import java.lang.reflect.Modifier

class Aspector(
  private val classMaker: ClassMaker,
) {
  fun <T: Any> applyAspect(
    aspectDeclare: ClassDecl<*>,
    targetClass: ClassDecl<T>,
  ): AspectDecl<T> {
    checkAspectDeclare(aspectDeclare, targetClass)
    classMaker.checkAspectable(aspectDeclare, targetClass)

    val aspectDecl = classMaker.makeClass(aspectDeclare, targetClass) {
      val stub = findStub(aspectDeclare)

      // Register Stub spec
      stub.forEach {
        registerStubSpec(it)
      }

      val nonStubInterfaces = aspectDeclare.annotatedInterfaces
        .filter { it.getAnnotation(Stub::class.asName()) == null }
        .map { it.type }
      // Register implement interfaces
      nonStubInterfaces.forEach {
        registerInterfaces(it)
      }

      // Register aspect methods
      aspectDeclare.methods
        .filter { !Modifier.isPrivate(it.flags) && !Modifier.isStatic(it.flags) }
        .map {
          it to (it.getAnnotation(AspectElement::class.asName())
            ?.getValue<EnumValue<Using>>("using")
            ?.value
            ?: Using.OVERRIDE)
        }
        .forEach { (method, using) ->
          registerAspectMethod(
            EAspectMethod(
              method.declaring,
              method.name,
              method.parameters,
              method.annotatedReturnType,
              method.flags,
              using,
              method.annotations
            )
          )
        }

      // Register fields
      aspectDeclare.fields
        .forEach { field -> registerImplField(field) }

      // Register non-aspect methods
      aspectDeclare.methods
        .filter { method -> Modifier.isPrivate(method.flags) || Modifier.isStatic(method.flags) }
        .forEach { method -> registerImplMethod(method) }

      // Register constructor
      aspectDeclare.constructors
        .forEach { constructor -> registerImplConstructor(constructor) }
    }

    return aspectDecl
  }

  private fun checkAspectDeclare(aspectDecl: ClassDecl<*>, targetClass: ClassDecl<*>) {
    // Check source type
    if (targetClass.let {
      it.isPrimitive || it.isEnum || it.isArray || it.isInterface
    }) throw IllegalArgumentException("Source class ${aspectDecl.name} must be a normal class")

    // Check implement type
    if (aspectDecl.let {
      it.isPrimitive || it.isEnum || it.isArray || it.isInterface
    }) throw IllegalArgumentException("Aspect implement class ${aspectDecl.name} must be a normal class")

    // Check stub, super class must be Stub
    if (aspectDecl.annotatedSuperClass?.let {
      it.type.name != ClassName.jObject && !it.annotations.any{ a -> a.type == Stub::class.asName() }
    } ?: false) throw AspectDeclaringException("Super class of aspect implement must be annotated by @Stub")
  }

  private fun findStub(aspectImpl: ClassDecl<*>) =
    (listOfNotNull(aspectImpl.annotatedSuperClass) + aspectImpl.annotatedInterfaces)
      .filter { it.annotations.any { a -> a.type == Stub::class.asName() } }
      .map { it.type }
      .toSet()
}