package fsutils

import java.io.{
  BufferedOutputStream, BufferedInputStream, ByteArrayOutputStream, File,
  FileNotFoundException, FileOutputStream, FileInputStream, FileWriter,
}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}
import scala.io.Source
import cats.MonadError
import cats.effect.Sync
import cats.implicits._
import org.apache.commons.compress.compressors.{CompressorOutputStream, CompressorInputStream}
import CompressionAlgorithm.CompressionAlgorithm

sealed trait FSObject[F[_]] {

  def getAbsolutePath: F[String]

  def size: F[Long]

  def toJavaFile: File
}

final case class FSFile[F[_]](
  private val handle: File,
)(implicit private val S: Sync[F], private val E: MonadError[F, Throwable]) extends FSObject[F] {

  def getLines: Iterator[String] =
    Source.fromFile(this.handle).getLines

  def copyTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.delay {
        Files.copy(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
        ()
      }
    )

  def moveTo(destination: String): F[Unit] =
    this.getAbsolutePath.flatMap(path =>
      S.delay {
        Files.move(
          Paths.get(path),
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING,
        )
        ()
      }
    )

  def renameTo(destination: String): F[Unit] =
    S.delay {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }.fold(E.raiseError[Unit], S.pure)
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
      }.fold(E.raiseError[Unit], S.pure)
    }.flatten

  def writeLinesProgressively(lines: => Iterator[_], chunkSize: Int = 10000): F[Unit] =
    S.delay {
      Using(new FileWriter(handle))(writer =>
        lines
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString(NewLine.toString) :+ NewLine))
      ).fold(E.raiseError[Unit], S.pure)
    }.flatten

  protected case class TransientCompressedFile(
    private val handle: FSFile[F], private val algo: CompressionAlgorithm,
  ) {

    private val compressor: ByteArrayOutputStream => CompressorOutputStream =
      CompressionUtils.getCompressor(algo)

    def writeTo(targetPath: String): F[FSFile[F]] =
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
            }.flatten *>
            FSFile.fromFile[F](targetFile)
          }.flatten.fold(E.raiseError[FSFile[F]], S.pure)
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
          }.flatten.fold(E.raiseError[Array[Byte]], S.pure)
        }.flatten
      )
  }

  val compressWith: CompressionAlgorithm => TransientCompressedFile =
    TransientCompressedFile(this, _)

  protected case class TransientDecompressedFile(
    private val handle: FSFile[F], private val algo: CompressionAlgorithm,
  ) {

    private val decompressor: BufferedInputStream => CompressorInputStream =
      DecompressionUtils.getDecompressor(algo)

    // TODO: improve this very inefficient method
    def writeTo(destinationPath: String): F[FSFile[F]] =
      S.delay {
        val bis = new BufferedInputStream(new FileInputStream(handle.toJavaFile))
        val inputStream = decompressor(bis)
        val bufferedSrc  = scala.io.Source.fromInputStream(inputStream)
        val destinationFile = new File(destinationPath)
        Using(new BufferedOutputStream(new FileOutputStream(destinationFile))) {
          _.write(bufferedSrc.iter.toArray.map(_.toByte))
        }.fold(E.raiseError[FSFile[F]], _ => S.pure(FSFile(destinationFile)))
      }.flatten
  }

  val decompressFrom: CompressionAlgorithm => TransientDecompressedFile =
    TransientDecompressedFile(this, _)

  def toJavaFile: File = this.handle
}

object FSFile {

  def createAt[F[_]](filePath: String)(implicit S: Sync[F]): F[FSFile[F]] =
    S.delay {
      val file = new File(filePath)
      if (!file.exists) {
        file.getParentFile.mkdirs
        file.createNewFile
      }
      FSFile(file)
    }

  def fromPath[F[_]](filePath: String)
                    (implicit S: Sync[F], E: MonadError[F, Throwable]): F[FSFile[F]] =
    S.delay {
      val file = new File(filePath)
      if (file.exists && file.isFile) S.pure(FSFile(file))
      else E.raiseError[FSFile[F]](new FileNotFoundException)
    }.flatten

  def fromFile[F[_]](file: File)(implicit S: Sync[F]): Try[FSFile[F]] =
    Try { assert(file.exists && file.isFile) }
      .map(_ => FSFile(file))

  def existsAt[F[_]](path: String)(implicit S: Sync[F]): F[Boolean] =
    S.delay {
      val handle = new File(path)
      handle.exists && handle.isFile
    }
}

final case class FSDirectory[F[_]](
  private val handle: File,
)(implicit private val S: Sync[F], private val E: MonadError[F, Throwable]) extends FSObject[F] {

  def renameTo(destination: String)(implicit S: Sync[F], E: MonadError[F, Throwable]): F[Unit] =
    S.delay {
      Try { assert(this.handle.renameTo(new File(destination))) }
        .fold(E.raiseError[Unit], S.pure)
    }.flatten

  def getAbsolutePath: F[String] =
    S.delay { handle.getAbsolutePath }

  def delete: F[Unit] =
    S.delay { handle.delete }
      .flatMap(
        if (_) S.unit
        else E.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  def getContents: F[Array[FSObject[F]]] =
    S.delay {
      handle.listFiles.map({
        case f if f.isFile => FSFile.fromFile(f)
        case d             => FSDirectory.fromFile(d)
      }).flatMap(_.toOption)
    }

  def getDirectoriesBelow: F[Array[FSDirectory[F]]] =
    this.getContents.map(_ collect { case d: FSDirectory[F] => d })

  def getFilesBelow: F[Array[FSFile[F]]] =
    this.getContents.map(_ collect { case f: FSFile[F] => f })

  def forEachFileBelow[A](cb: (String, FSFile[F]) => F[A]): F[List[A]] = {

    def forEachFileIn(path: String, fsObj: FSObject[F]): F[List[A]] =
      fsObj match {
        case f: FSFile[F] => cb(path, f).map(List(_))
        case d: FSDirectory[F] =>
          d
            .getContents
            .flatMap(
              _
                .toList
                .traverse(file => file.getAbsolutePath.flatMap(forEachFileIn(_, file)))
                .map(_.flatten)
            )
      }

      this.getAbsolutePath.flatMap(forEachFileIn(_, this))
    }

  def size: F[Long] =
    forEachFileBelow((_, f) => f.size).map(_.sum)

  def getFilePathsBelow: F[List[String]] =
    forEachFileBelow((_, f) => f.getAbsolutePath)

  def lastModified: F[Long] =
    S.delay { handle.lastModified }

  def toJavaFile: File = this.handle
}

object FSDirectory {

  def createAt[F[_]](directoryPath: String)(implicit S: Sync[F]): F[FSDirectory[F]] =
    S.delay {
      val directory = new File(directoryPath)
      if (!directory.exists) {
        directory.getParentFile.mkdirs
        directory.mkdir
      }
      FSDirectory(directory)
    }

  def fromPath[F[_]](directoryPath: String)
                    (implicit S: Sync[F], E: MonadError[F, Throwable]): F[FSDirectory[F]] =
    S.delay {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) S.pure(FSDirectory(directory))
      else E.raiseError[FSDirectory[F]](new FileNotFoundException)
    }.flatten

  def fromFile[F[_]](directory: File)(implicit S: Sync[F]): Try[FSDirectory[F]] =
    Try { assert(directory.exists && directory.isDirectory) }
      .map(_ => FSDirectory(directory))

  def existsAt[F[_]](path: String)(implicit S: Sync[F]): F[Boolean] =
    S.delay {
      val handle = new File(path)
      handle.exists && handle.isDirectory
    }
}
