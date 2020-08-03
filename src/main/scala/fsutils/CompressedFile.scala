package fsutils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.apache.commons.compress.compressors._
import org.apache.commons.compress.compressors.bzip2._
import org.apache.commons.compress.compressors.deflate._
import org.apache.commons.compress.compressors.gzip._
import org.apache.commons.compress.compressors.lz4._
import org.apache.commons.compress.compressors.lzma._
import org.apache.commons.compress.compressors.pack200._
import org.apache.commons.compress.compressors.snappy._
import org.apache.commons.compress.compressors.xz._
import org.apache.commons.compress.compressors.zstandard._
import java.io.FileInputStream
import java.io.BufferedInputStream

object Compression extends Enumeration {

  type Compression = Value
  val DEFLATE, BZ2, GZ, PACK200, XZ, ZSTANDARD, LZMA, LZ4, SNAPPY = Value
}

import Compression._

object CompressionUtils {

  def getCompressor(compression: Compression, bos: ByteArrayOutputStream): CompressorOutputStream =
    compression match {
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
}

object DecompressionUtils {

  def getDecompressor(compression: Compression, bis: BufferedInputStream): CompressorInputStream =
    compression match {
      case BZ2       => new BZip2CompressorInputStream(bis)
      case DEFLATE   => new DeflateCompressorInputStream(bis)
      case GZ        => new GzipCompressorInputStream(bis)
      case LZMA      => new LZMACompressorInputStream(bis)
      case LZ4       => new FramedLZ4CompressorInputStream(bis)
      case PACK200   => new Pack200CompressorInputStream(bis)
      case SNAPPY    => new FramedSnappyCompressorInputStream(bis)
      case XZ        => new XZCompressorInputStream(bis)
      case ZSTANDARD => new ZstdCompressorInputStream(bis)
    }
}
