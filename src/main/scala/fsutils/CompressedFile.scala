package fsutils

import java.io.{BufferedOutputStream, ByteArrayOutputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import scala.util.Using
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import cats.implicits._
import cats.effect.IO

object Compression extends Enumeration {

  type Compression = Value
  val DEFLATE, BZ2, GZ, PACK200, XZ, ZSTANDARD, LZMA, LZ4, SNAPPY = Value
}

import Compression._

case class CompressedFile(private val file: FSFile, algorithm: Compression) {

  private val compressedStream: ByteArrayOutputStream => CompressorOutputStream =
    algorithm match {
      case BZ2       => new BZip2CompressorOutputStream(_)
      case DEFLATE   => new DeflateCompressorOutputStream(_)
      case GZ        => new GzipCompressorOutputStream(_)
      case LZMA      => new LZMACompressorOutputStream(_)
      case LZ4       => new FramedLZ4CompressorOutputStream(_)
      case PACK200   => new Pack200CompressorOutputStream(_)
      case SNAPPY    => new FramedSnappyCompressorOutputStream(_)
      case XZ        => new XZCompressorOutputStream(_)
      case ZSTANDARD => new ZstdCompressorOutputStream(_)
    }

  def writeTo(directory: FSDirectory): IO[Unit] =
    IO {
      val byteArray = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      Using(new ByteArrayOutputStream(byteArray.size)) { bos =>
        Using(compressedStream(bos)) { compressed =>
          compressed.write(byteArray)
          Using(new BufferedOutputStream(new FileOutputStream(directory.toJavaFile))) {
            _.write(bos.toByteArray)
          }
        }
      }.flatten.flatten.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten
}
