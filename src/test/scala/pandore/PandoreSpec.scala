import org.scalatest.flatspec.AnyFlatSpec
import java.io.File
import cats.effect.IO
import pandore._

class ArtemisSpec extends AnyFlatSpec {

  behavior of "the fromFile and fromPath constructor methods"

  val path = "/tmp/test.txt"

  def withNonExisting(testFun: File => Any): Any = {
    val sampleFile = new File(path)
    if (sampleFile.exists) sampleFile.delete()
    testFun(sampleFile)
  }

  it should "fail to create a file handle from a non-existing file" in withNonExisting { sampleFile =>
    val res = FileHandle.fromFile[IO](sampleFile).attempt.unsafeRunSync()
    assert(res.isLeft)
    assert(res.swap.toOption.get.getClass.getCanonicalName === "java.io.FileNotFoundException")
  }

  it should "fail to create a file handle from a non-existing path" in withNonExisting { sampleFile =>
    val res = FileHandle.fromPath[IO](sampleFile.getAbsolutePath).attempt.unsafeRunSync()
    assert(res.isLeft)
    assert(res.swap.toOption.get.getClass.getCanonicalName === "java.io.FileNotFoundException")
  }

  def withExisting(testFun: File => Any): Any = {
    val sampleFile = new File(path)
    sampleFile.createNewFile()
    testFun(sampleFile)
    sampleFile.delete()
  }

  it should "create a file handle from an existing file" in withExisting { sampleFile =>
    val res = FileHandle.fromFile[IO](sampleFile).attempt.unsafeRunSync()
    assert(res.isRight)
    assert(res.toOption.get.toJavaFile === sampleFile)
  }

  it should "create a file handle from an existing path" in withExisting { sampleFile =>
    val res = FileHandle.fromPath[IO](path).attempt.unsafeRunSync()
    assert(res.isRight)
    assert(res.toOption.get.toJavaFile === sampleFile)
  }

  behavior of "the existsAt static FileHandle method"

  it should "return false when no file exists at that path" in withNonExisting { sampleFile =>

    assert(!FileHandle.existsAt[IO](sampleFile.getAbsolutePath).unsafeRunSync())
  }

  it should "return true when a file exists at that path" in withExisting { sampleFile =>

    assert(FileHandle.existsAt[IO](sampleFile.getAbsolutePath).unsafeRunSync())
  }
}
