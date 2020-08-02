package fsutils

import java.io.{BufferedOutputStream, FileOutputStream}
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
import java.nio.file.{Files, Paths}
import java.io.ByteArrayOutputStream

object Compression extends Enumeration {

  type Compression = Value
  val DEFLATE, BZ2, GZ, PACK200, XZ, ZSTANDARD, LZMA, LZ4, SNAPPY = Value
}

import Compression._

case class CompressedFile(private val file: FSFile, algorithm: Compression) {

  private def compressor(bos: ByteArrayOutputStream): CompressorOutputStream =
    algorithm match {
      case BZ2       => new BZip2CompressorOutputStream(bos)
      case DEFLATE   => new DeflateCompressorOutputStream(bos)
      case GZ        => new GzipCompressorOutputStream(bos)
      case LZMA      => new LZMACompressorOutputStream(bos)
      case LZ4       => new FramedLZ4CompressorOutputStream(bos)
      case PACK200   => new Pack200CompressorOutputStream(bos)
      case SNAPPY    => new FramedSnappyCompressorOutputStream(bos)
      case XZ        => new XZCompressorOutputStream(bos)
      case ZSTANDARD => new ZstdCompressorOutputStream(bos)
    }

  def writeTo(directory: FSDirectory): IO[Unit] =
    IO {
      val byteArray = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      Using(new ByteArrayOutputStream(byteArray.size)) { bos =>
        Using(compressor(bos)) { zstd =>
          zstd.write(byteArray)
          bos.toByteArray
          Using(new BufferedOutputStream(new FileOutputStream(directory.toJavaFile))) {
            _.write(bos.toByteArray)
          }
        }.flatten
      }.flatten.fold(IO.raiseError[Unit], IO.pure(_))
    }.flatten
}
