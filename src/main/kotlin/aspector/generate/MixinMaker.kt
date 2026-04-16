package aspector.generate

import aspector.classes.BytecodeLoader
import aspector.classes.ClassAccessor
import aspector.classes.ClassDecl
import aspector.classes.ClassName

class MixinMaker(
  classAccessor: ClassAccessor,
): ClassMaker(classAccessor) {
  override fun generateClassName(
    aspectImpl: ClassDecl<*>,
    targetClass: ClassDecl<*>,
  ): ClassName {
    TODO("Not yet implemented")
  }

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
}