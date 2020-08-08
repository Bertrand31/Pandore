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
