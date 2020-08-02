package fsutils

import java.io.{BufferedOutputStream, File, FileNotFoundException, FileOutputStream, FileWriter}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}
import scala.io.Source
import cats.implicits._
import cats.effect.IO
import Compression._

sealed trait FSObject {

  def getAbsolutePath: String

  def size: IO[Long]

  def toJavaFile: File
}

final case class FSFile(private val handle: File) extends FSObject {

  def getLines: Iterator[String] =
    Source.fromFile(this.handle).getLines

  def copyTo(destination: String): IO[Unit] =
    IO {
      Files.copy(
        Paths.get(this.getAbsolutePath),
        Paths.get(destination),
        StandardCopyOption.REPLACE_EXISTING,
      )
    }.map(_ => ())

  def moveTo(destination: String): IO[Unit] =
    IO {
      Files.move(
        Paths.get(this.getAbsolutePath),
        Paths.get(destination),
        StandardCopyOption.REPLACE_EXISTING,
      )
    }.map(_ => ())

  def renameTo(destination: String): IO[Unit] =
    IO {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten

  def getAbsolutePath: String =
    handle.getAbsolutePath

  def size: IO[Long] =
    IO { handle.length }

  def delete: IO[Unit] =
    IO { handle.delete }
      .flatMap(
        if (_) IO.unit
        else IO.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  private val NewLine = '\n'
  private val NewLineByte = NewLine.toByte

  def writeByteLines[F[_]](data: Array[Array[Byte]]): IO[Unit] =
    IO {
      Using(new BufferedOutputStream(new FileOutputStream(handle, true))) { bos =>
        data.foreach(line => bos.write(line :+ NewLineByte))
      }.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten

  def writeLinesProgressively(lines: => Iterator[_], chunkSize: Int = 10000): IO[Unit] =
    IO {
      Using(new FileWriter(handle))(writer =>
        lines
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString(NewLine.toString) :+ NewLine))
      ).fold(IO.raiseError, IO.pure(_))
    }.flatten

  def compressTo: Compression => CompressedFile =
    CompressedFile(this, _)

  def toJavaFile: File = this.handle
}

object FSFile {

  def createAt(filePath: String): IO[FSFile] =
    IO {
      val file = new File(filePath)
      if (!file.exists) {
        file.getParentFile.mkdirs
        file.createNewFile
      }
      new FSFile(file)
    }

  def fromPath(filePath: String): IO[FSFile] =
    IO {
      val file = new File(filePath)
      if (file.exists && file.isFile) IO.pure(new FSFile(file))
      else IO.raiseError(new FileNotFoundException)
    }.flatten

  def fromFile(file: File): Try[FSFile] =
    Try {
      assert(file.exists && file.isFile)
      new FSFile(file)
    }
}

final case class FSDirectory(private val handle: File) extends FSObject {

  def renameTo(destination: String): IO[Unit] =
    IO {
      Try {
        assert(this.handle.renameTo(new File(destination)))
      }.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten

  def getAbsolutePath: String =
    handle.getAbsolutePath

  def delete: IO[Unit] =
    IO { handle.delete }
      .flatMap(
        if (_) IO.unit
        else IO.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  def getObjectsBelow: IO[Array[FSObject]] =
    IO {
      handle.listFiles.map({
        case f if f.isFile => FSFile.fromFile(f)
        case d             => FSDirectory.fromFile(d)
      }).flatMap(_.toOption)
    }

  def getFoldersBelow: IO[Array[FSDirectory]] =
    IO { handle.listFiles.map(FSDirectory.fromFile).flatMap(_.toOption) }

  def getFilesBelow: IO[Array[FSFile]] =
    IO { handle.listFiles.map(FSFile.fromFile).flatMap(_.toOption) }

  def forEachFileBelow[A](cb: (String, FSFile) => IO[A]): IO[List[A]] = {

    def forEachFileIn(path: String, fsObj: FSObject): IO[List[A]] =
      fsObj match {
        case f: FSFile => cb(path, f).map(List[A](_))
        case d: FSDirectory =>
          d
            .getObjectsBelow
            .flatMap(
              _
                .toList
                .traverse(file => forEachFileIn(file.getAbsolutePath, file))
                .map(_.flatten)
            )
      }

      forEachFileIn(getAbsolutePath, this)
    }

  def size: IO[Long] =
    forEachFileBelow((_, f) => f.size).map(_.sum)

  def toJavaFile: File = this.handle
}

object FSDirectory {

  def createAt(directoryPath: String): IO[FSDirectory] =
    IO {
      val directory = new File(directoryPath)
      if (!directory.exists) {
        directory.getParentFile.mkdirs
        directory.mkdir
      }
      new FSDirectory(directory)
    }

  def fromPath(directoryPath: String): IO[FSDirectory] =
    IO {
      val directory = new File(directoryPath)
      if (directory.exists && directory.isDirectory) IO.pure(new FSDirectory(directory))
      else IO.raiseError(new FileNotFoundException)
    }.flatten

  def fromFile(directory: File): Try[FSDirectory] =
    Try { assert(directory.exists && directory.isDirectory) }
      .map(_ => FSDirectory(directory))
}
