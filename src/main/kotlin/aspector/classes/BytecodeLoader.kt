package aspector.classes

interface BytecodeLoader {
  fun declareClass(name: String, bytecode: ByteArray)
  fun loadClass(name: String): Class<*>
}