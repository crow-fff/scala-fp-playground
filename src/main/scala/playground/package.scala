import cats.effect.IO

package object playground {
  val readLn: IO[String] = IO(scala.io.StdIn.readLine())

  def putStrLn(s: String): IO[Unit] = IO(println(s))
}
