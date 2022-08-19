package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")
    }
  }

  val system = ActorSystem("DispatcherDemo") //, ConfigFactory.load().getConfig("dispathersDemo"))

  // method #1 - programmatically - in code
  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")
  val r = new Random()
//  for (i <- 1 to 1000)
//    actors(r.nextInt(10)) ! i

  // method #2 - from config
  val rtjvmActors = for (i <- 1 to 10) yield system.actorOf(Props[Counter], s"rtjvm_$i")
  val r2 = new Random()
//  for (i <- 1 to 1000)
//    rtjvmActors(r2.nextInt(10)) ! i

  /**
   * Dispatchers implement the executionContext trait
   */

  class DBActor extends Actor with ActorLogging {
    // solution #1 use dedicated dispatchers like context.system.dispatchers.lookup("my-dispatcher")
    // running Futures inside of actors is generally discouraged - starve context.dispatcher of running threads
    // if Futures need to be used inside actors, use dedicated dispatcher like context.system.dispatchers.lookup("my-dispatcher")
    implicit val executionContext: ExecutionContext = context.dispatcher
    // solution #2 use Router

    override def receive: Receive = {
      case message => Future {
        // wait on a resource
        Thread.sleep(5000)
        log.info(s"Success: ${message.toString}")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
//  dbActor ! "the meaning of life is 42"

  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"important message $i"
    dbActor ! message
    nonBlockingActor ! message
  }
}