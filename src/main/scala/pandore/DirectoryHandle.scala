package pandore

import java.io.{File, FileNotFoundException}
import scala.util.Try
import scala.reflect.ClassTag
import scala.collection.immutable.ArraySeq
import cats._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._

final case class DirectoryHandle[F[+_]](private val handle: File)(
  using private val S: Sync[F], private val E: MonadError[F, Throwable], private val P: Parallel[F],
  private val Tr: Traverse[ArraySeq],
) extends PureHandle[F] {

  def renameTo(destination: String): F[Unit] =
    S.delay {
      Try { assert(this.handle.renameTo(new File(destination))) }
    }.flatMap(S.fromTry)

  def getAbsolutePath: F[String] =
    S.delay { handle.getAbsolutePath }

  def deleteIfEmpty: F[Unit] =
    S.delay { handle.delete; () }

  def delete: F[Unit] =
    this.getContents.flatMap(_.traverse(_.delete)).map(_.combineAll) *> this.deleteIfEmpty

  def getContents: F[ArraySeq[PureHandle[F]]] =
    S.blocking {
      handle.listFiles.to(ArraySeq).traverse({
        case f if f.isFile => FileHandle.fromFile(f): F[PureHandle[F]]
        case d             => DirectoryHandle.fromFile(d): F[PureHandle[F]]
      })
    }.flatten

  def getDirectoriesBelow: F[ArraySeq[DirectoryHandle[F]]] =
    this.getContents.map(_ collect { case d: DirectoryHandle[F] => d })

  def getFilesBelow: F[ArraySeq[FileHandle[F]]] =
    this.getContents.map(_ collect { case f: FileHandle[F] => f })

  def forEachFileBelow[A](cb: FileHandle[F] => F[A], maxConcurrency: Int = 10)
                         (using C: Concurrent[F]): F[ArraySeq[A]] =
    getFilesBelow >>= (_.parTraverseN(maxConcurrency)(cb)(Tr, C))

  def size(using C: Concurrent[F]): F[Long] =
    forEachFileBelow(_.size).map(_.sum)

  def getFilePathsBelow(using C: Concurrent[F]): F[ArraySeq[String]] =
    forEachFileBelow(_.getAbsolutePath)

  def lastModified: F[Long] =
    S.blocking { handle.lastModified }

  def toJavaFile: File = this.handle
}

object DirectoryHandle {

  def createAt[F[+_]](path: String)(using S: Sync[F], P: Parallel[F]): F[DirectoryHandle[F]] =
    S.blocking {
      val directory = new File(path)
      if (!directory.exists) {
        directory.getParentFile.mkdirs
        directory.mkdir
      }
      DirectoryHandle(directory)
    }

  def fromPath[F[+_]](directoryPath: String)(
    using S: Sync[F], E: MonadError[F, Throwable], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.blocking {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromPathOrCreate[F[+_]](directoryPath: String)(
    using S: Sync[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.blocking {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else this.createAt(directoryPath)
    }.flatten

  def fromFile[F[+_]](directory: File)(
    using S: Sync[F], E: MonadError[F, Throwable], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.blocking {
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def existsAt[F[+_]](path: String)(using S: Sync[F]): F[Boolean] =
    S.blocking {
      val handle = new File(path)
      handle.exists && handle.isDirectory
    }
}
