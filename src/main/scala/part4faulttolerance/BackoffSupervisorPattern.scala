package part4faulttolerance

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{BackoffOpts, BackoffSupervisor}

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.io.Source

object BackoffSupervisorPattern extends App {

  case object ReadFile

  class FileBasedPersistenceActor extends Actor with ActorLogging {

    var dataSource: Source = null

    override def preStart(): Unit =
      log.info("Persistent actor starting")

    override def postStop(): Unit =
      log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null) {
          dataSource = Source.fromFile(new File("src/main/resources/important_data.txt"))
          log.info("I've just read some IMPORTANT data: " + dataSource.getLines().toList)
        }
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
//  val simpleActor = system.actorOf(Props[FileBasedPersistenceActor], "simpleActor")
//  simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onFailure(
      Props[FileBasedPersistenceActor],
      "simpleBackoffActor",
      FiniteDuration(3, TimeUnit.SECONDS),
      FiniteDuration(30, TimeUnit.SECONDS),
      0.2
    )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile
  /*
    simpleSupervisor
      - child called simpleBackoffActor (props of type FileBasedPersistenceActor)
      - supervision strategy is the default one (restarting on everything)
        - child actor failure restart
          - first attempt after 3 seconds
          - next attempt is 2x the previous attempt
          - for ex. 3 seconds, 6 s, 12 s, 24 s
   */

  val stopSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onStop(
      Props[FileBasedPersistenceActor],
      "stopBackoffActor",
      FiniteDuration(3, TimeUnit.SECONDS),
      FiniteDuration(30, TimeUnit.SECONDS),
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

//  val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
//  stopSupervisor ! ReadFile

  class EagerFBPActor extends FileBasedPersistenceActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/important_data.txt"))
    }
  }

  val eagerActor = system.actorOf(Props[EagerFBPActor])
  // ActorInitializationException => STOP
  val repeatedSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onStop(
      Props[EagerFBPActor],
      "eagerActor",
      FiniteDuration(1, TimeUnit.SECONDS),
      FiniteDuration(30, TimeUnit.SECONDS),
      0.1
    )
  )
  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

  /*
    eagerSupervisor
      - child eagerActor
        - will die on start with ActorInitializationException
        - trigger the supervision strategy in eagerSupervisor => STOP eagerActor
      - backoff will kick in after 1 second, 2s, 4s, 8s, 16s
   */

}
