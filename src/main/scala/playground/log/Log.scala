package playground.log

import cats.effect.Sync

trait Log[F[_]] {
  def info(message: String): F[Unit]
}

object Log {
  def apply[F[_]](implicit l: Log[F]): Log[F] = l

  def stdout[F[_]: Sync]: Log[F] = new Log[F] {
    override def info(message: String): F[Unit] = Sync[F].delay(println(message))
  }
}
