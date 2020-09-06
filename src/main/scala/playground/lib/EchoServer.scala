package playground.lib

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import cats.effect._
import cats.effect.concurrent._
import cats.syntax.all._
import fs2.io.tcp.{Socket, SocketGroup}
import fs2.{text, Pipe, Stream, Chunk}
import playground.log.Log

class EchoServer[F[_]: Concurrent : ContextShift : Log] private
                (socketGroup: SocketGroup,
                 port: Int)
                (stopFlag: MVar[F, Unit],
                 semaphore: Semaphore[F]) extends Server[F] {

  def serve: F[Unit] = {
    val startListening = socketGroup.server[F](new InetSocketAddress(port))
      .evalMap { client =>
        connectionHandler(client, semaphore)
      }
      .compile
      .drain

    for {
      _ <- Log[F].info(s"Starting the server on port $port")
      _ <- stopFlag.tryTake
      listenerFiber <- Concurrent[F].start(startListening)
      stoppingFiber <- Concurrent[F].start(stopFlag.read *> listenerFiber.cancel)
      _ <- Sync[F].guarantee(
        Concurrent[F].race(listenerFiber.join, stoppingFiber.join)
      )(Log[F].info(s"Shutting down the server"))
    } yield ()
  }

  def connectionHandler(socket: Resource[F, Socket[F]],
                        semaphore: Semaphore[F]): F[Fiber[F, Unit]] = {
    Concurrent[F].start(
      Resource.make(semaphore.tryAcquire) { acquired =>
        Concurrent[F].whenA(acquired)(semaphore.release)
      }.product(socket)
        .use { case (acquired, socket) =>
          for {
            _ <- if (acquired) {
              startSession(socket)
            } else for {
              addr <- socket.remoteAddress
              _ <- Log[F].info(s"Dropping client $addr")
              message = "Too many connections\n"
              _ <- socket.write(Chunk.indexedSeq(message.getBytes(StandardCharsets.US_ASCII)))
            } yield ()
          } yield ()
        }
    )
  }

  def startSession(socket: Socket[F]): F[Unit] = {
    val in = socket.reads(8).through(stringReader)

    val out: Pipe[F, String, Unit] =
      _.through(stringWriter)
        .through(socket.writes())

    for {
      addr <- socket.remoteAddress
      _ <- Log[F].info(s"New client $addr")
      _ <- Sync[F].guarantee(session(in, out)) {
        Log[F].info(s"Client $addr disconnected")
      }
    } yield ()
  }

  def session(in: Stream[F, String], out: Pipe[F, String, Unit]): F[Unit] = {
    val interrupter = Stream.eval(stopFlag.read)
      .map(_ => "Shutting down the server")

    val reads = in
      .takeThrough(_.nonEmpty)
      .takeThrough(_ != "shutdown")

    val writes: Pipe[F, String, Unit] =
      _.flatMap {
        case "" => Stream.emit("Good bye!")
        case "shutdown" =>
          Stream.eval_(stopFlag.put(()))
        case s => Stream.emit(s"Unknown command: $s")
      }
        .mergeHaltBoth(interrupter)
        .through(out)

    reads.through(writes).compile.drain
  }

  def stringReader: Pipe[F, Byte, String] =
    _.through(text.utf8Decode)
      .through(text.lines)

  def stringWriter: Pipe[F, String, Byte] = {
    _.through(
      _.flatMap(s => Stream(s.getBytes(StandardCharsets.US_ASCII).toIndexedSeq: _*).covary[F]
        .append(Stream.emit('\n'.toByte))
      )
    )
  }
}

object EchoServer {
  def applyF[F[_]: Concurrent : ContextShift : Log]
           (socketGroup: SocketGroup,
            port: Int,
            maxConnections: Int): F[Server[F]] = for {
    stopFlag <- MVar.empty[F, Unit]
    semaphore <- Semaphore[F](maxConnections.toLong)
  } yield new EchoServer[F](socketGroup, port)(stopFlag, semaphore)
}
