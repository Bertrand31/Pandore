# FS Utils

FS utils is a library that grew out of being tired of writing the same "File Utils" over and over in
every Scala project I work on.

Not only it is repetitive but it is extremely tedious. Java's file handling APIs, although
very comprehensive and powerful, are painful to use and remember, especially when
compression/decompression is involved.

The goal of this library is mostly for me to write all of this once and for all, and being able to
reuse it. As such, some things may be missing, and some others are strongly opinionated (for example
the use of the IO monad).
Also, because I am really no expert in Java there will definitely be room for improvements in some
places.

For all these reasons, everyone is welcome to contribute to this library to improve it, add
functionalities, challenge some of its opinions (why not make it configurable such that the user can
chose to have the side-effecting methods returns ZIO monads, or Futures, etc.).

## Overview

Here are some simple, self-explanatory uses of the library:

```scala
import fsutils._

for {
  file     <- FSFile.createAt("/my/new/file.csv")
  _        <- file.writeLinesProgressively(Iterator("foo", "bar", "bar"))
  fileSize <- file.size
  _        <- logger.debug(s"Successfuly wrote a $fileSize bytes file")
  _        <- file.compressWith(CompressionAlgorithm.ZSTANDARD).writeTo("/x/y/file.csv.zstd")
} yield ()
```

```scala
import fsutils._

for {
  dir         <- FSDirectory.fromFile(new File("/foo/bar")
  _           <- dir.forEachFileBelow((path: String, file: FSFile) => file.moveTo(path ++ ".old"))
  contentSize <- dir.size
} yield contentSize
```

```scala
import fsutils._

for {
  file             <- FSFile.fromPath("/x/y/z.txt.snappy")
  decompressedFile <- file.decompressFrom(CompressionAlgorithm.SNAPPY).writeTo("/x/y/z.txt")
} yield decompressedFile
```
