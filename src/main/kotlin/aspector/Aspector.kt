package aspector

import aspector.annotations.AspectExtends
import aspector.annotations.AspectElement
import aspector.annotations.Shared
import aspector.annotations.Stub
import aspector.classes.ArrayValue
import aspector.generate.IllegalAspectDeclaringException
import aspector.generate.AspectFactory
import aspector.classes.ClassDecl
import aspector.classes.ClassName
import aspector.classes.EAspectMethod
import aspector.classes.EnumValue
import aspector.classes.TypeValue
import aspector.generate.AspectFactory.Companion.asName
import java.lang.reflect.Modifier

class Aspector(
  private val aspectFactory: AspectFactory,
) {
  fun <T: Any> applyAspect(
    targetClass: ClassDecl<T>,
    vararg aspectClasses: ClassDecl<*>,
  ): AspectDecl<T> {
    val aspectLayers = mutableListOf<MutableList<ClassDecl<*>>>()

    val queue = aspectClasses.map { it to 0 }.toMutableList()
    val solved = mutableSetOf<ClassDecl<*>>()

    while (queue.isNotEmpty()) {
      val curr = queue.removeAt(0)
      val clazz = curr.first
      val depth = curr.second

      if (solved.add(clazz)) {
        while (aspectLayers.size <= depth) aspectLayers.add(mutableListOf()) // may use 'if'?
        aspectLayers[depth].add(clazz)

        val extensions = clazz.getAnnotation(ClassName.byClass(AspectExtends::class))
          ?.getValue<ArrayValue<Class<*>, TypeValue>>("extends")
          ?.rawValue
          ?.map { it.rawValue } ?: emptyList()

        extensions.forEach {
          val classDecl = aspectFactory.classAccessor.getClassDecl<Any>(it)
          queue.add(classDecl to depth + 1)
        }
      }
    }

    val flat = aspectLayers.flatMap { it }
    checkAspectDeclare(targetClass, flat)
    aspectFactory.checkAspectable(targetClass, flat)

    val aspectDecl = aspectFactory.makeClass(targetClass, *flat.toTypedArray()) {
      val stub = flat.flatMap { i ->
        (listOfNotNull(i.annotatedSuperClass) + i.annotatedInterfaces)
          .mapNotNull { it.annotations.find { a -> a.type == Stub::class.asName() }?.let { stub -> it.type to stub } }
      }.toMap()
      val nonStubInterfaces = flat.flatMap { i ->
        i.annotatedInterfaces
          .filter { it.getAnnotation(Stub::class.asName()) == null }
          .map { it.type }
      }.toSet()

      // Register Stub spec
      stub.forEach { (decl, anno) ->
        registerStubSpec(
          decl.name,
          anno.getValue<TypeValue>("attacheTo")!!.rawValue
        )
      }

      // Register implement interfaces
      nonStubInterfaces.forEach {
        registerInterfaces(it.name)
      }

      flat.forEach { decl ->
        // Register fields
        decl.fields
          .forEach { field ->
            if (field.getAnnotation(Shared::class.asName()) != null) {
              registerSharedField(field)
            }
            else registerDeclField(field)
          }

        // Register non-aspect methods
        decl.methods
          .filter { method ->
            Modifier.isPrivate(method.flags)
            || Modifier.isStatic(method.flags)
            || Modifier.isFinal(method.flags)
          }
          .forEach { method -> registerDeclMethod(method) }

        // Register constructor
        decl.constructors
          .forEach { constructor -> registerDeclConstructor(constructor) }
      }

      aspectLayers.forEachIndexed { layer, decls ->
        decls.forEach { decl ->
          // Register aspect methods
          decl.methods
            .filter {
              !Modifier.isPrivate(it.flags)
              && !Modifier.isStatic(it.flags)
              && !Modifier.isFinal(it.flags)
            }
            .map {
              it to (it.getAnnotation(AspectElement::class.asName())
                       ?.getValue<EnumValue<Using>>("using")
                       ?.value
                     ?: Using.OVERRIDE)
            }
            .forEach { (method, using) ->
              registerAspectMethod(
                layer,
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
        }
      }
    }

    return aspectDecl
  }

  private fun checkAspectDeclare(targetClass: ClassDecl<*>, aspectClasses: List<ClassDecl<*>>) {
    // Check source type
    if (targetClass.let {
      it.isPrimitive || it.isEnum || it.isArray || it.isInterface
    }) throw IllegalArgumentException("Source class ${targetClass.name} must be a normal class")

    aspectClasses.forEach { decl ->
      // Check implement type
      if (decl.let {
          it.isPrimitive || it.isEnum || it.isArray || it.isInterface
        }) throw IllegalArgumentException("Aspect implement class ${decl.name} must be a normal class")

      // Check stub, super class must be Stub
      if (decl.annotatedSuperClass?.let {
          it.type.name != ClassName.jObject && !it.annotations.any{ a -> a.type == Stub::class.asName() }
        } ?: false) throw IllegalAspectDeclaringException("Super class of aspect implement must be annotated by @Stub")
    }
  }
}