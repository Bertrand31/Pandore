package pandore

import java.io.{File, FileNotFoundException}
import scala.util.Try
import scala.reflect.ClassTag
import scala.collection.immutable.ArraySeq
import cats._
import cats.effect.Async
import cats.effect.std.Semaphore
import cats.implicits._

final case class DirectoryHandle[F[+_]](private val handle: File)(
  using private val A: Async[F], private val E: MonadError[F, Throwable],
  private val P: Parallel[F],
) extends PureHandle[F] {

  def renameTo(destination: String): F[Unit] =
    A.delay {
      Try { assert(this.handle.renameTo(new File(destination))) }
        .fold(E.raiseError, A.pure)
    }.flatten

  def getAbsolutePath: F[String] =
    A.delay { handle.getAbsolutePath }

  def deleteIfEmpty: F[Unit] =
    A.delay { handle.delete; () }

  def delete: F[Unit] =
    this.getContents.flatMap(_.traverse(_.delete)).map(_.combineAll) *> this.deleteIfEmpty

  def getContents: F[ArraySeq[PureHandle[F]]] =
    A.delay {
      handle.listFiles.to(ArraySeq).traverse({
        case f if f.isFile => FileHandle.fromFile(f): F[PureHandle[F]]
        case d             => DirectoryHandle.fromFile(d): F[PureHandle[F]]
      })
    }.flatten

  def getDirectoriesBelow: F[ArraySeq[DirectoryHandle[F]]] =
    this.getContents.map(_ collect { case d: DirectoryHandle[F] => d })

  def getFilesBelow: F[ArraySeq[FileHandle[F]]] =
    this.getContents.map(_ collect { case f: FileHandle[F] => f })

  def forEachFileBelow[A](cb: FileHandle[F] => F[A], maxConcurrency: Int = 1000)
                         (using T: ClassTag[A]): F[ArraySeq[A]] =
    Semaphore[F](maxConcurrency).flatMap { semaphore =>
      val throttledCallback = RateLimiting.throttle(semaphore, cb)

      lazy val throttledExplore: PureHandle[F] => F[ArraySeq[A]] =
        RateLimiting.throttle(semaphore, {
          case f: FileHandle[F]      => throttledCallback(f).map(ArraySeq(_))
          case d: DirectoryHandle[F] => d.getContents >>= (_.parFlatTraverse(throttledExplore))
        })

      throttledExplore(this)
    }

  def size: F[Long] =
    forEachFileBelow(_.size).map(_.sum)

  def getFilePathsBelow: F[ArraySeq[String]] =
    forEachFileBelow(_.getAbsolutePath)

  def lastModified: F[Long] =
    A.delay { handle.lastModified }

  def toJavaFile: File = this.handle
}

object DirectoryHandle {

  def createAt[F[+_]](path: String)(using A: Async[F], P: Parallel[F]): F[DirectoryHandle[F]] =
    A.delay {
      val directory = new File(path)
      if (!directory.exists) {
        directory.getParentFile.mkdirs
        directory.mkdir
      }
      DirectoryHandle(directory)
    }

  def fromPath[F[+_]](directoryPath: String)(
    using A: Async[F], E: MonadError[F, Throwable], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    A.delay {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) A.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromPathOrCreate[F[+_]](directoryPath: String)(
    using A: Async[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    A.delay {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) A.pure(DirectoryHandle(directory))
      else this.createAt(directoryPath)
    }.flatten

  def fromFile[F[+_]](directory: File)(
    using A: Async[F], E: MonadError[F, Throwable], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    A.delay {
      if (directory.exists && directory.isDirectory) A.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def existsAt[F[+_]](path: String)(using A: Async[F]): F[Boolean] =
    A.delay {
      val handle = new File(path)
      handle.exists && handle.isDirectory
    }
}
