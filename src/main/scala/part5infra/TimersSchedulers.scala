package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, PoisonPill, Props, Timers}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulersTimersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  import system.dispatcher

//  system.scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
//    simpleActor ! "reminder"
//  }
//
//  val routine = system.scheduler.scheduleWithFixedDelay(FiniteDuration(1, TimeUnit.SECONDS), FiniteDuration(2, TimeUnit.SECONDS)) {
//    () => simpleActor ! "heartbeat"
//  }
//
//  system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS)) {
//    routine.cancel()
//  }

  /*
    Exercise: implement a self-closing actor
      - if the actor receives a message (anything), you have 1 second to sent it another message
      - if the time window expires, the actor will stop itself
      - if you send another message, the time window is reset
   */

  class SelfClosingActor extends Actor with ActorLogging {
    var closeCancellable: Cancellable = createTimeoutWindow()

    def createTimeoutWindow(): Cancellable = {
      system.scheduler.scheduleOnce(FiniteDuration(1, TimeUnit.SECONDS)) {
        self ! "timeout"
      }
    }

    override def receive: Receive = {
      case "timeout" =>
        log.info("Stopping myself")
        context.stop(self)
      case message =>
        log.info(s"Received $message, staying alive")
        closeCancellable.cancel()
        closeCancellable = createTimeoutWindow()
    }
  }

//  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
//  selfClosingActor ! "hi there!"
//  system.scheduler.scheduleOnce(FiniteDuration(500, TimeUnit.MILLISECONDS)) {
//    selfClosingActor ! "how are you?"
//  }
//  system.scheduler.scheduleOnce(FiniteDuration(750, TimeUnit.MILLISECONDS)) {
//    selfClosingActor ! "i'm doing great"
//  }
//  system.scheduler.scheduleOnce(FiniteDuration(2, TimeUnit.SECONDS)) {
//    selfClosingActor ! "are you still there?"
//  }

  /*
  Timer
   */

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, FiniteDuration(500, TimeUnit.MILLISECONDS))

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startTimerAtFixedRate(TimerKey, Reminder, FiniteDuration(1, TimeUnit.SECONDS))
      case Reminder => log.info("I am alive")
      case Stop =>
        log.warning("Stopping!")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerBasedHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS)) {
    timerBasedHeartbeatActor ! Stop
  }
}
