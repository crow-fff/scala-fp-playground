package playground.app


import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors

import cats.MonadError
import cats.effect.{ContextShift, Blocker, ExitCode, IOApp, Concurrent, IO}
import cats.syntax.all._
import playground._

import scala.concurrent.ExecutionContext

object FileCopyApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    IO(Executors.newFixedThreadPool(2)).bracket { blockingExecutor =>

      ExecutionContext.fromExecutor(blockingExecutor)
      implicit val blocker: Blocker = Blocker.liftExecutorService(blockingExecutor)

      for {
        nb <- program[IO](args)
        _ <- putStrLn(s"$nb byte transferred")
      } yield ExitCode.Success
    } { executor => IO(executor.shutdown()) }
  }

  def getArgs[F[_]](args: List[String])(implicit F: MonadError[F, Throwable]): F[(String, String)] = {
    if (args.size != 2)
      F.raiseError(new IllegalArgumentException("expected exactly 2 arguments"))
    else
      F.pure((args(0), args(1)))
  }

  def program[F[_]: ContextShift](args: List[String])
                                 (implicit b: Blocker, F: Concurrent[F]): F[Long] = {
    for {
      (src, tgt) <- getArgs[F](args)
      srcPath <- F.delay(Paths.get(src))
      tgtPath <- F.delay(Paths.get(tgt))
      bytesTransferred <- transfer[F](srcPath, tgtPath)
    } yield bytesTransferred
  }

  def transfer[F[_]: ContextShift](src: Path, tgt: Path, chunkSize: Int = 4096)
                                              (implicit b: Blocker, F: Concurrent[F]): F[Long] = {
    val input = F.delay(Files.newInputStream(src))
    val output = F.delay(Files.newOutputStream(tgt))

    val reader = fs2.io.readInputStream[F](input, chunkSize, b)
    val writer = fs2.io.writeOutputStream[F](output, b)

    reader
      .observe(writer)
      .compile
      .foldChunks(0L)((agg, c) => agg + c.size)
  }
}
