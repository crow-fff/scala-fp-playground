package playground.app

import java.util.concurrent.Executors

import cats.effect._
import fs2.io.tcp.SocketGroup
import playground.lib.EchoServer
import playground.log.Log

object EchoServerApp extends IOApp {
  implicit val log: Log[IO] = Log.stdout[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val program = for {
      blockingPool <- Resource.make {
        IO(Executors.newFixedThreadPool(4))
      } { pool => IO(pool.shutdown()) }
      blocker = Blocker.liftExecutorService(blockingPool)
      group <- SocketGroup[IO](blocker)
      server <- Resource.liftF(EchoServer.applyF[IO](group, port = 8080, 2))
    } yield server

    program.use(_.serve) *> IO.pure(ExitCode.Success)
  }

}
