package pandore

import java.io.{
  BufferedOutputStream, BufferedInputStream, ByteArrayOutputStream, File,
  FileNotFoundException, FileOutputStream, FileInputStream, FileWriter,
}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}
import scala.io.Source
import cats.MonadError
import cats.effect.Async
import cats.implicits._
import org.apache.commons.compress.compressors.{CompressorOutputStream, CompressorInputStream}
import compression.{CompressionUtils, DecompressionUtils}
import compression.CompressionAlgorithm.CompressionAlgorithm

final case class FileHandle[F[_]](private val handle: File)(
  using private val A: Async[F],
  private val E: MonadError[F, Throwable],
) extends PureHandle[F] {

  def getLines: Iterator[String] =
    Source.fromFile(this.handle).getLines()

  def copyTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      A.delay {
        Files.copy(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def moveTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      A.delay {
        Files.move(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def renameTo(destination: String): F[Unit] =
    A.delay {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }.fold(E.raiseError, A.pure).void
    }.flatten

  def getAbsolutePath: F[String] =
    A.delay { handle.getAbsolutePath }

  def size: F[Long] =
    A.delay { handle.length }

  def delete: F[Unit] =
    A.delay { handle.delete }
      .flatMap(
        if (_) A.unit
        else E.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  def canRead: F[Boolean] =
    A.delay { handle.canRead }

  def canWrite: F[Boolean] =
    A.delay { handle.canWrite }

  def canExecute: F[Boolean] =
    A.delay { handle.canExecute }

  def isHidden: F[Boolean] =
    A.delay { handle.isHidden }

  def lastModified: F[Long] =
    A.delay { handle.lastModified }

  private val NewLine = '\n'
  private val NewLineByte = NewLine.toByte

  def writeByteLines(data: Array[Array[Byte]]): F[Unit] =
    A.delay {
      Using(new BufferedOutputStream(new FileOutputStream(handle, true))) { bos =>
        data.foreach(line => bos.write(line :+ NewLineByte))
      }.fold(E.raiseError, A.pure).void
    }.flatten

  def writeLinesProgressively(lines: => IterableOnce[_], chunkSize: Int = 10000): F[Unit] =
    A.delay {
      Using(new FileWriter(handle))(writer =>
        lines
          .iterator
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString(NewLine.toString) :+ NewLine))
      ).fold(E.raiseError, A.pure).void
    }.flatten

  protected case class TransientCompressedFile(
    private val handle: FileHandle[F], private val algo: CompressionAlgorithm,
  ) {

    private val compressor: ByteArrayOutputStream => CompressorOutputStream =
      CompressionUtils.getCompressor(algo)

    def writeTo(targetPath: String): F[FileHandle[F]] =
      handle.getAbsolutePath.flatMap(path =>
        A.delay[F[FileHandle[F]]] {
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
        A.delay[F[Array[Byte]]] {
          val byteArray = Files.readAllBytes(Paths.get(path))
          Using(new ByteArrayOutputStream(byteArray.size)) { bos =>
            Using(compressor(bos)) { compressed =>
              compressed.write(byteArray)
              bos.toByteArray
            }
          }.flatten.fold(E.raiseError, A.pure)
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
      A.delay[F[FileHandle[F]]] {
        val bis = new BufferedInputStream(new FileInputStream(handle.toJavaFile))
        val inputStream = decompressor(bis)
        val bufferedSrc  = scala.io.Source.fromInputStream(inputStream)
        val destinationFile = new File(destinationPath)
        Using(new BufferedOutputStream(new FileOutputStream(destinationFile))) {
          _.write(bufferedSrc.iter.toArray.map(_.toByte))
        }.fold(E.raiseError, _ => A.pure(FileHandle(destinationFile)))
      }.flatten
  }

  val decompressFrom: CompressionAlgorithm => TransientDecompressedFile =
    TransientDecompressedFile(this, _)

  def toJavaFile: File = this.handle
}

object FileHandle {

  def createAt[F[_]](filePath: String)(using A: Async[F]): F[FileHandle[F]] =
    A.delay {
      val file = new File(filePath)
      if (!file.exists) {
        file.getParentFile.mkdirs
        file.createNewFile
      }
      FileHandle(file)
    }

  def fromPath[F[_]](filePath: String)(using A: Async[F]): F[FileHandle[F]] =
    this.fromFile(new File(filePath))

  def fromPathOrCreate[F[_]](filePath: String)(using A: Async[F]): F[FileHandle[F]] =
    A.delay {
      val file = new File(filePath)
      if (file.exists && file.isFile) A.pure(FileHandle(file))
      else this.createAt(filePath)
    }.flatten

  def fromFile[F[_]](file: File)
                    (using A: Async[F], E: MonadError[F, Throwable]): F[FileHandle[F]] =
    A.delay[F[FileHandle[F]]] {
      if (file.exists && file.isFile) A.pure(FileHandle(file))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromFileOrCreate[F[+_]](file: File)(using A: Async[F]): F[FileHandle[F]] =
    A.delay {
      if (file.exists && file.isFile) A.pure(FileHandle(file))
      else this.createAt(file.getAbsolutePath)
    }.flatten

  def existsAt[F[_]](path: String)(using A: Async[F]): F[Boolean] =
    A.delay {
      val handle = new File(path)
      handle.exists && handle.isFile
    }
}
