package pandore

import java.io.{BufferedInputStream, ByteArrayOutputStream}
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

object CompressionAlgorithm extends Enumeration {

  type CompressionAlgorithm = Value
  val DEFLATE, BZ2, GZ, PACK200, XZ, ZSTANDARD, LZMA, LZ4, SNAPPY = Value
}

import CompressionAlgorithm._

object CompressionUtils {

  def getCompressor(algo: CompressionAlgorithm): ByteArrayOutputStream => CompressorOutputStream =
    algo match {
      case BZ2       => new BZip2CompressorOutputStream(_)
      case DEFLATE   => new DeflateCompressorOutputStream(_)
      case GZ        => new GzipCompressorOutputStream(_)
      case LZMA      => new LZMACompressorOutputStream(_)
      case LZ4       => new FramedLZ4CompressorOutputStream(_)
      case PACK200   => new Pack200CompressorOutputStream(_)
      case SNAPPY    => new FramedSnappyCompressorOutputStream(_)
      case XZ        => new XZCompressorOutputStream(_)
      case ZSTANDARD => new ZstdCompressorOutputStream(_, 8)
    }
}

object DecompressionUtils {

  def getDecompressor(algo: CompressionAlgorithm): BufferedInputStream => CompressorInputStream =
    algo match {
      case BZ2       => new BZip2CompressorInputStream(_)
      case DEFLATE   => new DeflateCompressorInputStream(_)
      case GZ        => new GzipCompressorInputStream(_)
      case LZMA      => new LZMACompressorInputStream(_)
      case LZ4       => new FramedLZ4CompressorInputStream(_)
      case PACK200   => new Pack200CompressorInputStream(_)
      case SNAPPY    => new FramedSnappyCompressorInputStream(_)
      case XZ        => new XZCompressorInputStream(_)
      case ZSTANDARD => new ZstdCompressorInputStream(_)
    }
}
