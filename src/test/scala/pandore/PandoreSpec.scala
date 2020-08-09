import org.scalatest.flatspec.AnyFlatSpec
import java.io.File
import cats.effect.IO
import pandore._

class ArtemisSpec extends AnyFlatSpec {

  behavior of "fromFile constructor method"

  val path = "/tmp/test.txt"

  def withNonExisting(testFun: File => Any): File = {
    val sampleFile = new File(path)
    sampleFile.createNewFile()
    sampleFile
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

  def withExisting(testFun: File => Any): File = {
    val sampleFile = new File(path)
    sampleFile.createNewFile()
    sampleFile
  }

  it should "create a file handle from an existing file" in withExisting { sampleFile =>
    val res = FileHandle.fromFile[IO](sampleFile).attempt.unsafeRunSync()
    assert(res.isRight)
    assert(res.toOption.get.toJavaFile === sampleFile)
  }

  it should "create a file handle from an existing path" in withExisting { sampleFile =>
    val res = FileHandle.fromPath[IO](path).attempt.unsafeRunSync()
    assert(res.isRight)
    assert(res.toOption.get === sampleFile)
  }
}
