package pandore.compression

import java.io.BufferedInputStream
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

object DecompressionUtils {

  import CompressionAlgorithm._

  val getDecompressor: CompressionAlgorithm => BufferedInputStream => CompressorInputStream =
    _ match {
      case DEFLATE   => new DeflateCompressorInputStream(_)
      case BZ2       => new BZip2CompressorInputStream(_)
      case GZ        => new GzipCompressorInputStream(_)
      case PACK200   => new Pack200CompressorInputStream(_)
      case XZ        => new XZCompressorInputStream(_)
      case ZSTANDARD => new ZstdCompressorInputStream(_)
      case LZMA      => new LZMACompressorInputStream(_)
      case LZ4       => new FramedLZ4CompressorInputStream(_)
      case SNAPPY    => new FramedSnappyCompressorInputStream(_)
    }
}
