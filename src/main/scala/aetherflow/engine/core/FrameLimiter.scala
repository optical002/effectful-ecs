package aetherflow.engine.core

import aetherflow.engine.core.logger.ASyncLogger
import aetherflow.engine.performance.FrameDuration.logger
import zio.*

class FrameLimiter(
  frameRate: Int,
  frameCoordinator: FrameCoordinator,
  clock: Clock
){
  private val logger = new ASyncLogger("Frame-Limiter")

  def run = { 
    logger.logVerbose("Starting") *>
    (for {
      _ <- logger.logVerbose("Waiting for next frame")
      _ <- frameCoordinator.signalReady(FrameCoordinator.SignalFrom.FrameLimiter)
      startedAtNanos <- clock.nanoTime
      toSleepNanos = 1_000_000_000 / frameRate
      _ <- logger.logVerbose(s"Sleeping for ${1_000 / frameRate} millis (frame rate = $frameRate)")
      _ <- {
        def waitUntil(targetTimeNanos: Long): UIO[Unit] =
          clock.nanoTime.flatMap { now =>
            if (now >= targetTimeNanos) ZIO.unit
            else ZIO.yieldNow *> waitUntil(targetTimeNanos)
          }
  
        // more precise than sleep, since sleep reschedules fiber running time
        // which gives an overhead of around 10-30ms
        waitUntil(startedAtNanos + toSleepNanos)
      }
      _ <- logger.logVerbose("Waking up")
    } yield ()).forever.onDone(
      error = _ => logger.logVerbose("Closing with error"),
      success = _ => logger.logVerbose("Closing")
    ).fork
  }
}
object FrameLimiter {
  def layer(frameRate: Int) = ZLayer.fromZIO(for {
    frameCoordinator <- ZIO.service[FrameCoordinator]
    clock <- ZIO.clock
  } yield new FrameLimiter(frameRate, frameCoordinator, clock))
}
