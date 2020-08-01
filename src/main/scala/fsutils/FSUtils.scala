package fsutils

import java.io.{BufferedOutputStream, File, FileOutputStream}
import scala.util.Using
import cats.implicits._
import cats.effect.IO
import scala.util.Try
import java.io.FileWriter

sealed trait FSObject {

  def getAbsolutePath: IO[String]
}

case class FSFile(private val handle: File) extends FSObject {

  def getAbsolutePath: IO[String] =
    IO { handle.getAbsolutePath }

  def size: IO[Long] =
    IO { handle.length }

  def delete: IO[Unit] =
    IO { handle.delete }
      .flatMap(
        if (_) IO.unit
        else IO.raiseError(new RuntimeException(s"Could not delete ${handle.getPath}"))
      )

  private val NewLine = '\n'.toByte

  def writeByteLines[F[_]](data: Array[Array[Byte]]): IO[Unit] =
    IO {
      Using(new BufferedOutputStream(new FileOutputStream(handle, true))) { bos =>
        data.foreach(line => bos.write(line :+ NewLine))
      }.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten

  def writeLinesProgressively(lines: => Iterator[_], chunkSize: Int = 10000): IO[Unit] =
    IO {
      Using.resource(new FileWriter(handle))(writer =>
        lines
          .sliding(chunkSize, chunkSize)
          .foreach((writer.write(_: String)) compose (_.mkString("\n") :+ '\n'))
      )
    }
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

  def fromFile(file: File): Try[FSFile] =
    Try {
      assert(file.exists && file.isFile)
      new FSFile(file)
    }
}

case class FSDirectory(private val handle: File) extends FSObject {

  def getAbsolutePath: IO[String] =
    IO { handle.getAbsolutePath }

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
                .map(file => file.getAbsolutePath.flatMap(forEachFileIn(_, file)))
                .traverse(identity)
                .map(_.flatten)
            )
      }

      getAbsolutePath.flatMap(forEachFileIn(_, this))
    }
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

  def fromFile(directory: File): Try[FSDirectory] =
    Try {
      assert(directory.exists && directory.isDirectory)
      new FSDirectory(directory)
    }
}
