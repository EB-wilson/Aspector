package aspector

import aspector.generate.ClassMaker
import aspector.classes.BytecodeLoader
import aspector.classes.ClassName

abstract class AspectDecl<T: Any>(
  protected val context: ClassMaker.AspectBuilder,
){
  abstract fun getClassName(): ClassName
  abstract fun getBytecode(): ByteArray
  abstract fun load(loader: BytecodeLoader): Class<T>
}