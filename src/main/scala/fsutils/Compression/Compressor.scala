package fsutils

import cats.effect.IO

trait Compressor {

  def compress(file: FSFile): IO[Array[Byte]]
}
