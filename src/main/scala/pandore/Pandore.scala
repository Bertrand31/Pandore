package pandore

import java.io.{
  BufferedOutputStream, BufferedInputStream, ByteArrayOutputStream, File,
  FileNotFoundException, FileOutputStream, FileInputStream, FileWriter,
}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}
import scala.reflect.ClassTag
import scala.io.Source
import scala.collection.immutable.ArraySeq
import cats.{MonadError, Parallel}
import cats.effect.{Concurrent, concurrent, Sync}, concurrent.Semaphore
import cats.implicits._
import org.apache.commons.compress.compressors.{CompressorOutputStream, CompressorInputStream}
import CompressionAlgorithm.CompressionAlgorithm

sealed trait PureHandle[F[+_]] {

  def getAbsolutePath: F[String]

  def size: F[Long]

  def delete: F[Unit]

  def toJavaFile: File
}

final case class FileHandle[F[+_]](private val handle: File)(
  implicit private val S: Sync[F], private val E: MonadError[F, Throwable],
  private val C: Concurrent[F], private val P: Parallel[F],
) extends PureHandle[F] {

  def getLines: Iterator[String] =
    Source.fromFile(this.handle).getLines()

  def copyTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.delay {
        Files.copy(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def moveTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.delay {
        Files.move(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def renameTo(destination: String): F[Unit] =
    S.delay {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }.fold(E.raiseError, S.pure)
    }.flatten

  def getAbsolutePath: F[String] =
    S.delay { handle.getAbsolutePath }

  def size: F[Long] =
    S.delay { handle.length }

  def delete: F[Unit] =
    S.delay { handle.delete }
      .flatMap(
        if (_) S.unit
        else E.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  def canRead: F[Boolean] =
    S.delay { handle.canRead }

  def canWrite: F[Boolean] =
    S.delay { handle.canWrite }

  def canExecute: F[Boolean] =
    S.delay { handle.canExecute }

  def isHidden: F[Boolean] =
    S.delay { handle.isHidden }

  def lastModified: F[Long] =
    S.delay { handle.lastModified }

  private val NewLine = '\n'
  private val NewLineByte = NewLine.toByte

  def writeByteLines(data: Array[Array[Byte]]): F[Unit] =
    S.delay {
      Using(new BufferedOutputStream(new FileOutputStream(handle, true))) { bos =>
        data.foreach(line => bos.write(line :+ NewLineByte))
      }.fold(E.raiseError, S.pure)
    }.flatten

  def writeLinesProgressively(lines: => IterableOnce[_], chunkSize: Int = 10000): F[Unit] =
    S.delay {
      Using(new FileWriter(handle))(writer =>
        lines
          .iterator
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString(NewLine.toString) :+ NewLine))
      ).fold(E.raiseError, S.pure)
    }.flatten

  protected case class TransientCompressedFile(
    private val handle: FileHandle[F], private val algo: CompressionAlgorithm,
  ) {

    private val compressor: ByteArrayOutputStream => CompressorOutputStream =
      CompressionUtils.getCompressor(algo)

    def writeTo(targetPath: String): F[FileHandle[F]] =
      handle.getAbsolutePath.flatMap(path =>
        S.delay {
          val targetFile = new File(targetPath)
          val byteArray = Files.readAllBytes(Paths.get(path))
          Using(new ByteArrayOutputStream(byteArray.size)) { bos =>
            Using(compressor(bos)) { compressed =>
              compressed.write(byteArray)
              Using(new BufferedOutputStream(new FileOutputStream(targetFile))) {
                _.write(bos.toByteArray)
              }
            }.flatten
          }.flatten.fold(E.raiseError, _ => FileHandle.fromFile(targetFile))
        }.flatten
      )

    def toByteArray: F[Array[Byte]] =
      handle.getAbsolutePath.flatMap(path =>
        S.delay {
          val byteArray = Files.readAllBytes(Paths.get(path))
          Using(new ByteArrayOutputStream(byteArray.size)) { bos =>
            Using(compressor(bos)) { compressed =>
              compressed.write(byteArray)
              bos.toByteArray
            }
          }.flatten.fold(E.raiseError, S.pure)
        }.flatten
      )
  }

  val compressWith: CompressionAlgorithm => TransientCompressedFile =
    TransientCompressedFile(this, _)

  protected case class TransientDecompressedFile(
    private val handle: FileHandle[F], private val algo: CompressionAlgorithm,
  ) {

    private val decompressor: BufferedInputStream => CompressorInputStream =
      DecompressionUtils.getDecompressor(algo)

    // TODO: improve this very inefficient method
    def writeTo(destinationPath: String): F[FileHandle[F]] =
      S.delay {
        val bis = new BufferedInputStream(new FileInputStream(handle.toJavaFile))
        val inputStream = decompressor(bis)
        val bufferedSrc  = scala.io.Source.fromInputStream(inputStream)
        val destinationFile = new File(destinationPath)
        Using(new BufferedOutputStream(new FileOutputStream(destinationFile))) {
          _.write(bufferedSrc.iter.toArray.map(_.toByte))
        }.fold(E.raiseError, _ => S.pure(FileHandle(destinationFile)))
      }.flatten
  }

  val decompressFrom: CompressionAlgorithm => TransientDecompressedFile =
    TransientDecompressedFile(this, _)

  def toJavaFile: File = this.handle
}

object FileHandle {

  def createAt[F[+_]](filePath: String)
                     (implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]): F[FileHandle[F]] =
    S.delay {
      val file = new File(filePath)
      if (!file.exists) {
        file.getParentFile.mkdirs
        file.createNewFile
      }
      FileHandle(file)
    }

  def fromPath[F[+_]](filePath: String)
                     (implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]): F[FileHandle[F]] =
    S.delay { this.fromFile(new File(filePath)) }.flatten

  def fromPathOrCreate[F[+_]](filePath: String)
                      (implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]): F[FileHandle[F]] =
    S.delay {
      val file = new File(filePath)
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else this.createAt(filePath)
    }.flatten

  def fromFile[F[+_]](file: File)(
    implicit S: Sync[F], E: MonadError[F, Throwable], C: Concurrent[F], P: Parallel[F]
  ): F[FileHandle[F]] =
    S.delay {
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromFileOrCreate[F[+_]](file: File)
                      (implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]): F[FileHandle[F]] =
    S.delay {
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else this.createAt(file.getAbsolutePath)
    }.flatten

  def existsAt[F[+_]](path: String)(implicit S: Sync[F]): F[Boolean] =
    S.delay {
      val handle = new File(path)
      handle.exists && handle.isFile
    }
}

final case class DirectoryHandle[F[+_]](private val handle: File)(
  implicit private val S: Sync[F], private val E: MonadError[F, Throwable],
  private val C: Concurrent[F], private val P: Parallel[F],
) extends PureHandle[F] {

  def renameTo(destination: String): F[Unit] =
    S.delay {
      Try { assert(this.handle.renameTo(new File(destination))) }
        .fold(E.raiseError, S.pure)
    }.flatten

  def getAbsolutePath: F[String] =
    S.delay { handle.getAbsolutePath }

  def deleteIfEmpty: F[Unit] =
    S.delay { handle.delete; () }

  def delete: F[Unit] =
    this.getContents.flatMap(_.traverse(_.delete)).map(_.combineAll) *> this.deleteIfEmpty

  def getContents: F[ArraySeq[PureHandle[F]]] =
    S.delay {
      handle.listFiles.to(ArraySeq).traverse({
        case f if f.isFile => FileHandle.fromFile(f)
        case d             => DirectoryHandle.fromFile(d)
      })
    }.flatten

  def getDirectoriesBelow: F[ArraySeq[DirectoryHandle[F]]] =
    this.getContents.map(_ collect { case d: DirectoryHandle[F] => d })

  def getFilesBelow: F[ArraySeq[FileHandle[F]]] =
    this.getContents.map(_ collect { case f: FileHandle[F] => f })

  def forEachFileBelow[A](cb: FileHandle[F] => F[A], maxConcurrency: Int = 1000)
                      (implicit T: ClassTag[A]): F[ArraySeq[A]] =
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
    S.delay { handle.lastModified }

  def toJavaFile: File = this.handle
}

object DirectoryHandle {

  def createAt[F[+_]](directoryPath: String)(
    implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.delay {
      val directory = new File(directoryPath)
      if (!directory.exists) {
        directory.getParentFile.mkdirs
        directory.mkdir
      }
      DirectoryHandle(directory)
    }

  def fromPath[F[+_]](directoryPath: String)(
    implicit S: Sync[F], E: MonadError[F, Throwable], C: Concurrent[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.delay {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromPathOrCreate[F[+_]](directoryPath: String)(
    implicit S: Sync[F], C: Concurrent[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.delay {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else this.createAt(directoryPath)
    }.flatten

  def fromFile[F[+_]](directory: File)(
    implicit S: Sync[F], E: MonadError[F, Throwable], C: Concurrent[F], P: Parallel[F]
  ): F[DirectoryHandle[F]] =
    S.delay {
      if (directory.exists && directory.isDirectory) S.pure(DirectoryHandle(directory))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def existsAt[F[+_]](path: String)(implicit S: Sync[F]): F[Boolean] =
    S.delay {
      val handle = new File(path)
      handle.exists && handle.isDirectory
    }
}
