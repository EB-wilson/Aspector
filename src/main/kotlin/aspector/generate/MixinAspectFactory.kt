package aspector.generate

import aspector.classes.BytecodeLoader
import aspector.classes.ClassAccessor
import aspector.classes.ClassDecl
import aspector.classes.ClassName

class MixinAspectFactory(
  classAccessor: ClassAccessor,
): AspectFactory(classAccessor) {
  override fun generateClassName(
    targetClass: ClassDecl<*>,
    vararg aspectClasses: ClassDecl<*>,
  ): ClassName = targetClass.name

  override fun generateBytecode(builder: AspectBuilder): ByteArray {
    TODO("Not yet implemented")
  }

  override fun loadClass(
    loader: BytecodeLoader,
    className: ClassName,
    bytecode: ByteArray,
  ): Class<*> {
    TODO("Not yet implemented")
  }

  override fun checkAspectable(sourceClass: ClassDecl<*>, aspectClasses: List<ClassDecl<*>>) {
    TODO("Not yet implemented")
  }
}