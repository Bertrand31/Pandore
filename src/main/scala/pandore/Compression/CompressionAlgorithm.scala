package pandore.compression

object CompressionAlgorithm extends Enumeration {

  type CompressionAlgorithm = Value
  val DEFLATE, BZ2, GZ, PACK200, XZ, ZSTANDARD, LZMA, LZ4, SNAPPY = Value
}
