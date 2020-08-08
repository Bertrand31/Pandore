# Pandore

Pandore is a (tool)box that aims to hide all the ailments and complexities of Java's file handling
APIs and file compression/decompression from the ingenuous Functional Scala developper.

It does so by presenting a clean, coherent and fully functional interface, which acts as a one-stop
for all file and directories-related operations.

Everyone is welcome to contribute to this library to improve it, add functionalities or challenge
some of its opinionated approaches.

## Overview

Here are some simple, self-explanatory uses of the library:

```scala
import cats.effect.IO
import fsutils.{DirectoryHandle, FileHandle}

for {
  file     <- FileHandle.createAt[IO]("/my/new/file.csv")
  _        <- file.writeLinesProgressively(Iterator("foo", "bar", "bar"))
  fileSize <- file.size
  _        <- logger.debug(s"Successfuly wrote a $fileSize bytes file")
  _        <- file.compressWith(CompressionAlgorithm.ZSTANDARD).writeTo("/x/y/file.csv.zstd")
} yield ()
```

```scala
import cats.effect.IO
import fsutils._

for {
  dir         <- DirectoryHandle.fromFile[IO](new File("/foo/bar")
  contentSize <- dir.size
  _           <- dir.forEachFileBelow(uploadToS3)
  _           <- logger.debug(s"Uploaded $contentSize bytes to S3")
  _           <- dir.delete // Will recursively delete the directory and its contents
} yield ()
```

```scala
import cats.effect.IO
import fsutils._

for {
  file             <- FileHandle.fromPath[IO]("/x/y/z.txt.snappy")
  decompressedFile <- file.decompressFrom(CompressionAlgorithm.SNAPPY).writeTo("/x/y/z.txt")
} yield decompressedFile
```

If you ever were to decide to open Pandore's box, you can do so by calling the `.toJavaFile`
method on either of the data structures this library exposoes.
