package pandore

import java.io.File
import cats.effect.Concurrent

trait PureHandle[F[_]] {

  def getAbsolutePath: F[String]

  def size(using C: Concurrent[F]): F[Long]

  def delete: F[Unit]

  def toJavaFile: File
}
