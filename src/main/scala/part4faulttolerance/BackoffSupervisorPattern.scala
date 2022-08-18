package part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.io.Source
import java.io.File

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
          dataSource = Source.fromFile(new File("src/main/resources/important.txt"))
          log.info("I've just read some IMPORTANT data: " + dataSource.getLines().toList)
        }
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
  val simpleActor = system.actorOf(Props[FileBasedPersistenceActor], "simpleActor")
  simpleActor ! ReadFile
}
