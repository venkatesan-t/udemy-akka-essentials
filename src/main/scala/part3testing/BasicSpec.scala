package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Random
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll
{

  // setup
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import BasicSpec._

  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message

      expectMsg(message) // result timeout setting - akka.test.single-expect-default
    }

    "A blackhole actor" should {
      "send back the same message" in {
        val blackhole = system.actorOf(Props[Blackhole])
        val message = "hello, test"
        blackhole ! message

        expectNoMessage(FiniteDuration(1, TimeUnit.SECONDS))
      }
    }

    // message assertions
    "A lab test actor" should {
      val labTestActor = system.actorOf(Props[LabTestActor])

      "turn a string into uppercase" in {
        labTestActor ! "I love Akka"
        val reply = expectMsgType[String]
        assert(reply == "I LOVE AKKA")
      }

      "reply to a greeting" in {
        labTestActor ! "greeting"
        expectMsgAnyOf("hi", "hello")
      }

      "reply with favourite tech" in {
        labTestActor ! "favouriteTech"
        expectMsgAllOf("Scala", "Akka")
      }

      "reply with cool tech in a different way" in {
        labTestActor ! "favouriteTech"
        val messages = receiveN(2) // Seq[Any]

        // free to do more complicated assertions
      }

      "reply with cool tech in a fancy way" in {
        labTestActor ! "favouriteTech"

        expectMsgPF() {
          case "Scala" => //only care that the PF is defined
          case "Akka" =>
        }
      }
    }
  }

}

object BasicSpec {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  class Blackhole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()

    override def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
      case "favouriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase()
    }
  }
}
