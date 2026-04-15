package aspector.classes

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.*

class BytecodeClassLoader(
  parent: ClassLoader,
): ClassLoader(parent), BytecodeLoader {
  private val protocol = "byteloader-" + javaClass.getSimpleName() + hashCode()

  private val bytecodesMap = mutableMapOf<String, ByteArray>()
  private val bytecodesPaths = mutableMapOf<String, ByteArray>()

  override fun declareClass(
    name: String,
    bytecode: ByteArray,
  ) {
    if (bytecodesMap.containsKey(name))
      throw IllegalArgumentException("Class $name is already registered")

    val path = name.replace(".", "/") + ".class"
    bytecodesMap[name] = bytecode
    bytecodesPaths[path] = bytecode
  }

  @Suppress("DEPRECATION")
  public override fun findResource(name: String): URL? {
    val bytecode = bytecodesPaths[name]?: return null

    val handler = object: URLStreamHandler() {
      override fun openConnection(u: URL): URLConnection {
        return object: URLConnection(u) {
          var stream: ByteArrayInputStream? = null
          override fun connect() {
            stream = ByteArrayInputStream(bytecode)
          }
          @Throws(IOException::class)
          override fun getInputStream(): InputStream {
            connect()
            return stream!!
          }
          override fun getContentLengthLong(): Long = bytecode.size.toLong()
          override fun getContentType(): String = "application/octet-stream"
        }
      }
    }

    try {
      val result = URL(protocol, null, -1, name, handler)
      return result
    } catch (_: MalformedURLException) {
      return null
    }
  }

  public override fun findResources(name: String): Enumeration<URL?> {
    return object : Enumeration<URL?> {
      private var next = findResource(name)

      override fun hasMoreElements(): Boolean {
        return (next != null)
      }

      override fun nextElement(): URL {
        if (next == null) {
          throw NoSuchElementException()
        }
        val u = next
        next = null
        return u!!
      }
    }
  }

  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    return findLoadedClass(name)?: run {
      try {
        super.loadClass(name, resolve)
      } catch (e: ClassNotFoundException) {
        val bytecode = bytecodesMap[name]?: throw ClassNotFoundException(name)

        super.defineClass(name, bytecode, 0, bytecode.size).also {
          if (resolve) resolveClass(it)
        }
      }
    }
  }
}