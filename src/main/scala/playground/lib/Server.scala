package playground.lib

trait Server[F[_]] {
  def serve: F[Unit]
}
