package pandore

import java.io.{
  BufferedOutputStream, BufferedInputStream, ByteArrayOutputStream, File,
  FileNotFoundException, FileOutputStream, FileInputStream, FileWriter,
}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}
import scala.io.Source
import cats.MonadError
import cats.effect._
import cats.implicits._
import org.apache.commons.compress.compressors.{CompressorOutputStream, CompressorInputStream}
import compression.{CompressionUtils, DecompressionUtils}
import compression.CompressionAlgorithm.CompressionAlgorithm

final case class FileHandle[F[_]](private val handle: File)(
  using private val S: Sync[F],
  private val E: MonadError[F, Throwable],
) extends PureHandle[F] {

  def getLines: Iterator[String] =
    Source.fromFile(this.handle).getLines()

  def copyTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.blocking {
        Files.copy(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def moveTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.blocking {
        Files.move(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
      }.void
    )

  def renameTo(destination: String): F[Unit] =
    S.blocking {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }
    }.flatMap(S.fromTry).void

  def getAbsolutePath: F[String] =
    S.blocking { handle.getAbsolutePath }

  def size(using C: Concurrent[F]): F[Long] =
    S.blocking { handle.length }

  def delete: F[Unit] =
    S.blocking { handle.delete }
      .flatMap(
        if (_) S.unit
        else E.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  def canRead: F[Boolean] =
    S.blocking { handle.canRead }

  def canWrite: F[Boolean] =
    S.blocking { handle.canWrite }

  def canExecute: F[Boolean] =
    S.blocking { handle.canExecute }

  def isHidden: F[Boolean] =
    S.blocking { handle.isHidden }

  def lastModified: F[Long] =
    S.blocking { handle.lastModified }

  private val NewLine = '\n'
  private val NewLineByte = NewLine.toByte

  def writeByteLines(data: Array[Array[Byte]]): F[Unit] =
    S.blocking {
      Using(new BufferedOutputStream(new FileOutputStream(handle, true))) { bos =>
        data.foreach(line => bos.write(line :+ NewLineByte))
      }
    }.flatMap(S.fromTry).void

  def writeLinesProgressively(lines: => IterableOnce[_], chunkSize: Int = 10000): F[Unit] =
    S.blocking {
      Using(new FileWriter(handle))(writer =>
        lines
          .iterator
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString(NewLine.toString) :+ NewLine))
      )
    }.flatMap(S.fromTry).void

  protected case class TransientCompressedFile(
    private val handle: FileHandle[F], private val algo: CompressionAlgorithm,
  ) {

    private val compressor: ByteArrayOutputStream => CompressorOutputStream =
      CompressionUtils.getCompressor(algo)

    def writeTo(targetPath: String): F[FileHandle[F]] =
      handle.getAbsolutePath.flatMap(path =>
        S.blocking[F[FileHandle[F]]] {
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
        S.blocking[F[Array[Byte]]] {
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
      S.blocking[F[FileHandle[F]]] {
        val bis = new BufferedInputStream(new FileInputStream(handle.toJavaFile))
        val inputStream = decompressor(bis)
        val bufferedSrc  = Source.fromInputStream(inputStream)
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

  def createAt[F[_]](filePath: String)(using S: Sync[F]): F[FileHandle[F]] =
    S.blocking {
      val file = new File(filePath)
      if (!file.exists) {
        file.getParentFile.mkdirs
        file.createNewFile
      }
      FileHandle(file)
    }

  def fromPath[F[_]](filePath: String)(using S: Sync[F]): F[FileHandle[F]] =
    this.fromFile(new File(filePath))

  def fromPathOrCreate[F[_]](filePath: String)(using S: Sync[F]): F[FileHandle[F]] =
    S.blocking {
      val file = new File(filePath)
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else this.createAt(filePath)
    }.flatten

  def fromFile[F[_]](file: File)
                    (using S: Sync[F], E: MonadError[F, Throwable]): F[FileHandle[F]] =
    S.blocking[F[FileHandle[F]]] {
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else E.raiseError(new FileNotFoundException)
    }.flatten

  def fromFileOrCreate[F[+_]](file: File)(using S: Sync[F]): F[FileHandle[F]] =
    S.blocking {
      if (file.exists && file.isFile) S.pure(FileHandle(file))
      else this.createAt(file.getAbsolutePath)
    }.flatten

  def existsAt[F[_]](path: String)(using S: Sync[F]): F[Boolean] =
    S.blocking {
      val handle = new File(path)
      handle.exists && handle.isFile
    }
}
