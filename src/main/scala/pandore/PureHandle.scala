package pandore

import java.io.File

trait PureHandle[F[_]] {

  def getAbsolutePath: F[String]

  def size: F[Long]

  def delete: F[Unit]

  def toJavaFile: File
}
