package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  /*
    ResourceActor
      - open => it can receive read/write requests to the resource
      - otherwise it will postpone all read/write requests until the state is open

      Resource is closed
        - open => switch to the open state
        - Read, Write messages are POSTPONED

      Resource is open
        - Read, Write are handled
        - Close => switch to the closed state

      [Open, Read, Read, Write]
      - switch to the open state
      - read the data
      - read the data again
      - write the data

      [Read, Open, Write]
      - stash Read
        Stash (a special memory zone): [Read]
      - open => switch to the open state
      - prepend messages from stash to mailbox
        Mailbox: [Read, Write]
      - read and write are handled
   */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step 1 - mix-in the Stash trait
  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        // step 3 - unstashAll when we stash the message handler
        unstashAll()
        context.become(open)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed state")
        // step 2 - stash away what you can't handle
        stash()
    }

    def open: Receive = {
      case Read =>
        // do some actual computation
        log.info(s"I have read $innerData")
      case Write(data) =>
        log.info(s"I am writing $data")
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed state")
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Write("I love stash")
  resourceActor ! Read
  resourceActor ! Open

  resourceActor ! Read // stashed
  resourceActor ! Open // switch to open; I have read ""
  resourceActor ! Open // stashed
  resourceActor ! Write("I love stash") // I am writing I love stash
  resourceActor ! Close // switched to closed; switch to open because of Open message stashed
  resourceActor ! Read // I have read I love stash
}
